package takagi.ru.monica.autofilldebug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.autofill.AutofillManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import takagi.ru.monica.autofilldebug.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val logs = mutableListOf<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNativeForm()
        setupAppLikeForm()
        setupWebView()
        setupLogActions()
        setupUtilityActions()

        appendLog("LIFECYCLE", "MainActivity onCreate")
    }

    override fun onResume() {
        super.onResume()
        val currentAutofillService = Settings.Secure.getString(contentResolver, "autofill_service")
        appendLog(
            "STATE",
            "onResume, sdk=${Build.VERSION.SDK_INT}, autofill_service=${currentAutofillService ?: "null"}"
        )
    }

    private fun setupUtilityActions() {
        binding.btnOpenAutofillSettings.setOnClickListener {
            runCatching {
                startActivity(Intent("android.settings.AUTOFILL_SETTINGS"))
                appendLog("ACTION", "Open autofill settings")
            }.onFailure { e ->
                appendLog("ERROR", "Open autofill settings failed: ${e.message}")
            }
        }

        binding.btnClearAllAndRefocus.setOnClickListener {
            clearNativeForm()
            clearAppForm()
            clearWebForm()
            focusAndRequest(binding.nativeUsername, "native_username")
            appendLog("ACTION", "Clear all forms and refocus native username")
        }
    }

    private fun setupNativeForm() {
        installFieldLogger(binding.nativeUsername, "native_username")
        installFieldLogger(binding.nativePassword, "native_password")
        installFieldLogger(binding.nativeOtp, "native_otp")

        binding.btnNativeFocus.setOnClickListener {
            focusAndRequest(binding.nativeUsername, "native_username")
        }
        binding.btnNativeRequest.setOnClickListener {
            requestAutofill(binding.nativeUsername, "native_username")
        }
        binding.btnNativeClear.setOnClickListener {
            clearNativeForm()
            appendLog("ACTION", "Native form cleared")
        }
    }

    private fun setupAppLikeForm() {
        installFieldLogger(binding.appAccount, "app_account")
        installFieldLogger(binding.appSecret, "app_secret")
        installFieldLogger(binding.appNote, "app_note")

        binding.btnAppFocus.setOnClickListener {
            focusAndRequest(binding.appAccount, "app_account")
        }
        binding.btnAppRequest.setOnClickListener {
            requestAutofill(binding.appAccount, "app_account")
        }
        binding.btnAppClear.setOnClickListener {
            clearAppForm()
            appendLog("ACTION", "App-like form cleared")
        }
    }

    private fun setupWebView() {
        val webView = binding.webview
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.setSupportZoom(false)
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                appendLog("WEB", "Page loaded: ${url ?: "about:blank"}")
            }
        }
        webView.addJavascriptInterface(WebBridge { message ->
            runOnUiThread { appendLog("WEB", message) }
        }, "AndroidLog")

        binding.btnWebReload.setOnClickListener {
            loadWebForm()
            appendLog("ACTION", "Web form reloaded")
        }
        binding.btnWebFocus.setOnClickListener {
            webView.evaluateJavascript("focusUsername();", null)
            appendLog("ACTION", "Web focusUsername() called")
        }
        binding.btnWebClear.setOnClickListener {
            clearWebForm()
            appendLog("ACTION", "Web form cleared")
        }

        loadWebForm()
    }

    private fun setupLogActions() {
        binding.btnLogClear.setOnClickListener {
            logs.clear()
            binding.logText.text = ""
            appendLog("ACTION", "Logs cleared")
        }
        binding.btnLogCopy.setOnClickListener {
            val text = logs.joinToString(separator = "\n")
            val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            manager.setPrimaryClip(ClipData.newPlainText("autofill_debug_logs", text))
            Toast.makeText(this, "日志已复制", Toast.LENGTH_SHORT).show()
            appendLog("ACTION", "Logs copied to clipboard")
        }
        binding.btnLogShare.setOnClickListener {
            val text = logs.joinToString(separator = "\n")
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Autofill Debug Logs")
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(shareIntent, "分享日志"))
            appendLog("ACTION", "Logs shared")
        }
    }

    private fun installFieldLogger(editText: EditText, fieldName: String) {
        editText.setOnFocusChangeListener { _, hasFocus ->
            appendLog("FIELD", "$fieldName focus=$hasFocus")
            if (hasFocus) {
                requestAutofill(editText, fieldName)
            }
        }
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                appendLog("FIELD", "$fieldName text_length=${s?.length ?: 0}")
            }
        })
    }

    private fun clearNativeForm() {
        binding.nativeUsername.text?.clear()
        binding.nativePassword.text?.clear()
        binding.nativeOtp.text?.clear()
    }

    private fun clearAppForm() {
        binding.appAccount.text?.clear()
        binding.appSecret.text?.clear()
        binding.appNote.text?.clear()
    }

    private fun clearWebForm() {
        binding.webview.evaluateJavascript("clearForm();", null)
    }

    private fun focusAndRequest(view: View, fieldName: String) {
        view.requestFocus()
        requestAutofill(view, fieldName)
    }

    private fun requestAutofill(view: View, fieldName: String) {
        val manager = getSystemService(AutofillManager::class.java)
        if (manager == null) {
            appendLog("AUTOFILL", "AutofillManager unavailable for $fieldName")
            return
        }
        runCatching {
            manager.requestAutofill(view)
            appendLog("AUTOFILL", "requestAutofill($fieldName) sent")
        }.onFailure { e ->
            appendLog("AUTOFILL", "requestAutofill($fieldName) failed: ${e.message}")
        }
    }

    private fun loadWebForm() {
        binding.webview.loadDataWithBaseURL(
            "https://debug.autofill.local",
            WEB_FORM_HTML,
            "text/html",
            "utf-8",
            null
        )
    }

    private fun appendLog(tag: String, message: String) {
        val line = "${timeFormat.format(Date())} [$tag] $message"
        logs += line
        if (logs.size > 400) {
            logs.removeAt(0)
        }
        binding.logText.text = logs.joinToString(separator = "\n")
    }

    private class WebBridge(
        private val onLog: (String) -> Unit,
    ) {
        @JavascriptInterface
        fun post(message: String) {
            onLog(message)
        }
    }

    private companion object {
        private const val WEB_FORM_HTML = """
<!doctype html>
<html>
<head>
  <meta charset='utf-8'/>
  <meta name='viewport' content='width=device-width, initial-scale=1'/>
  <title>Autofill Debug Web Form</title>
  <style>
    body { font-family: sans-serif; margin: 14px; background: #f7fbff; }
    h3 { margin-top: 0; }
    .box { border: 1px solid #cdd8e3; border-radius: 8px; padding: 12px; background: #ffffff; }
    label { display: block; margin-top: 10px; font-size: 13px; color: #3d4a57; }
    input { width: 100%; box-sizing: border-box; padding: 10px; margin-top: 4px; border: 1px solid #b8c6d6; border-radius: 6px; }
    .actions { margin-top: 12px; display: flex; gap: 8px; }
    button { padding: 8px 10px; }
  </style>
</head>
<body>
  <div class='box'>
    <h3>WebView 登录页（debug.autofill.local）</h3>
    <label>用户名 / 邮箱</label>
    <input id='username' name='username' autocomplete='username email' />
    <label>密码</label>
    <input id='password' name='password' type='password' autocomplete='current-password' />
    <label>OTP（可选）</label>
    <input id='otp' name='otp' inputmode='numeric' autocomplete='one-time-code' />
    <div class='actions'>
      <button type='button' onclick='focusUsername()'>聚焦用户名</button>
      <button type='button' onclick='clearForm()'>清空表单</button>
    </div>
  </div>
  <script>
    function log(msg) {
      if (window.AndroidLog && window.AndroidLog.post) {
        window.AndroidLog.post(msg);
      }
    }
    function focusUsername() {
      document.getElementById('username').focus();
      log('focus username');
    }
    function clearForm() {
      document.getElementById('username').value = '';
      document.getElementById('password').value = '';
      document.getElementById('otp').value = '';
      log('clear form');
    }
    ['username', 'password', 'otp'].forEach(function(id) {
      const el = document.getElementById(id);
      el.addEventListener('focus', function() { log(id + ' focus'); });
      el.addEventListener('input', function() { log(id + ' len=' + el.value.length); });
    });
    log('web form ready');
  </script>
</body>
</html>
"""
    }
}
