package com.test.autofilltest

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding

class MainActivity : AppCompatActivity() {
    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var clearButton: Button
    private lateinit var resultTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 创建布局
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 标题
        val titleTextView = TextView(this).apply {
            text = "登录"
            textSize = 28f
            setTextColor(getColor(android.R.color.holo_blue_dark))
            setPadding(0, dpToPx(32), 0, dpToPx(8))
        }
        rootLayout.addView(titleTextView)

        // 副标题
        val subtitleTextView = TextView(this).apply {
            text = "测试 Monica 自动填充功能"
            textSize = 14f
            setTextColor(getColor(android.R.color.darker_gray))
            setPadding(0, 0, 0, dpToPx(32))
        }
        rootLayout.addView(subtitleTextView)

        // 用户名输入框
        usernameEditText = EditText(this).apply {
            hint = "用户名"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
            setPadding(dpToPx(16))
            textSize = 16f
            
            // 关键：设置自动填充提示
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
            setAutofillHints(View.AUTOFILL_HINT_USERNAME)
            
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(16)
            }
        }
        rootLayout.addView(usernameEditText)

        // 密码输入框
        passwordEditText = EditText(this).apply {
            hint = "密码"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(dpToPx(16))
            textSize = 16f
            
            // 关键：设置自动填充提示
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
            setAutofillHints(View.AUTOFILL_HINT_PASSWORD)
            
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(24)
            }
        }
        rootLayout.addView(passwordEditText)

        // 登录按钮
        loginButton = Button(this).apply {
            text = "登录"
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(56)
            ).apply {
                bottomMargin = dpToPx(16)
            }
            setOnClickListener {
                handleLogin()
            }
        }
        rootLayout.addView(loginButton)

        // 清除按钮
        clearButton = Button(this).apply {
            text = "清除"
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(24)
            }
            setOnClickListener {
                usernameEditText.text.clear()
                passwordEditText.text.clear()
                resultTextView.text = ""
                resultTextView.visibility = View.GONE
            }
        }
        rootLayout.addView(clearButton)

        // 结果显示
        resultTextView = TextView(this).apply {
            text = ""
            textSize = 14f
            setPadding(dpToPx(16))
            setBackgroundColor(getColor(android.R.color.holo_blue_light))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(24)
            }
        }
        rootLayout.addView(resultTextView)

        // 提示信息
        val infoTextView = TextView(this).apply {
            text = """
                测试说明：
                1. 确保已在系统设置中启用 Monica 自动填充服务
                2. 点击用户名或密码输入框
                3. 应该会看到 Monica 提供的密码建议
                4. ❌ 不要点击自动填充建议!
                5. ✅ 手动输入全新的用户名和密码
                6. 点击"登录"按钮
                7. 1.5秒后界面会自动关闭
                8. 应该会看到 Monica 的保存密码提示
            """.trimIndent()
            textSize = 12f
            setTextColor(getColor(android.R.color.darker_gray))
            setPadding(dpToPx(16))
            setBackgroundColor(getColor(android.R.color.holo_blue_bright))
        }
        rootLayout.addView(infoTextView)

        setContentView(rootLayout)
    }

    private fun handleLogin() {
        val username = usernameEditText.text.toString()
        val password = passwordEditText.text.toString()

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "请输入用户名和密码", Toast.LENGTH_SHORT).show()
            return
        }

        resultTextView.apply {
            text = "登录成功！\n用户名: $username\n密码: ${password.replace(Regex("."), "*")}"
            setTextColor(getColor(android.R.color.holo_green_dark))
            visibility = View.VISIBLE
        }
        
        Toast.makeText(this, "登录成功！", Toast.LENGTH_SHORT).show()
        
        // 🔥 关键修复: 延迟后关闭 Activity,触发 onSaveRequest
        // 这样 Android 系统才能检测到表单提交并触发密码保存提示
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            finish()  // 关闭 Activity - 这会触发 onSaveRequest!
        }, 1500)  // 1.5秒后关闭,让用户看到成功消息
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
