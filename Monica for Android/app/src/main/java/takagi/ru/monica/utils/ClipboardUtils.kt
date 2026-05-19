package takagi.ru.monica.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/**
 * Clipboard utility for secure password copying
 */
class ClipboardUtils(private val context: Context) {
    
    /**
     * Copy text to clipboard. Credential labels honor the app-level clipboard
     * auto-clear setting unless [autoClearSeconds] is passed explicitly.
     */
    fun copyToClipboard(
        text: String,
        label: String = "Monica Password",
        autoClearSeconds: Int? = null,
        sensitive: Boolean = isCredentialLabel(label)
    ) {
        Companion.copyToClipboard(
            context = context,
            text = text,
            label = label,
            autoClearSeconds = autoClearSeconds,
            sensitive = sensitive
        )
    }

    fun cancelAutoClear() = cancelPendingAutoClear()

    companion object {
        private val clipboardScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var clearClipboardJob: Job? = null

        fun copyToClipboard(
            context: Context,
            text: String,
            label: String = "Monica Password",
            autoClearSeconds: Int? = null,
            sensitive: Boolean = isCredentialLabel(label)
        ) {
            val appContext = context.applicationContext
            val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, text).apply {
                if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    description.extras = PersistableBundle().apply {
                        putBoolean("android.content.extra.IS_SENSITIVE", true)
                    }
                }
            }
            clipboard.setPrimaryClip(clip)

            clearClipboardJob?.cancel()
            clearClipboardJob = clipboardScope.launch {
                val seconds = autoClearSeconds ?: if (sensitive) {
                    SettingsManager(appContext).settingsFlow.first().clipboardAutoClearSeconds
                } else {
                    0
                }
                if (seconds > 0) {
                    delay(seconds * 1000L)
                    clearClipboardIfExpectedOrUnverifiable(appContext, label, text)
                }
            }
        }

        fun cancelPendingAutoClear() {
            clearClipboardJob?.cancel()
        }

        fun isCredentialLabel(label: String): Boolean {
            val normalized = label.trim().lowercase()
            return normalized.contains("password") ||
                normalized.contains("username") ||
                normalized.contains("user name") ||
                normalized.contains("account") ||
                normalized.contains("密码") ||
                normalized.contains("用户名") ||
                normalized.contains("账号") ||
                normalized.contains("帳號") ||
                normalized.contains("使用者")
        }

        internal fun shouldClearDelayedClipboard(
            snapshot: ClipboardSnapshot,
            expectedLabel: String,
            expectedText: String
        ): Boolean {
            return !snapshot.canVerify ||
                (snapshot.text == expectedText && snapshot.label == expectedLabel)
        }

        private fun clearClipboardIfExpectedOrUnverifiable(
            context: Context,
            label: String,
            text: String
        ) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val snapshot = readClipboardSnapshot(context, clipboard)
            if (shouldClearDelayedClipboard(snapshot, label, text)) {
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        clipboard.clearPrimaryClip()
                    } else {
                        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                }
            }
        }

        private fun readClipboardSnapshot(
            context: Context,
            clipboard: ClipboardManager
        ): ClipboardSnapshot {
            return runCatching {
                val currentClip = clipboard.primaryClip
                if (currentClip == null) {
                    ClipboardSnapshot(text = null, label = null, canVerify = false)
                } else {
                    ClipboardSnapshot(
                        text = currentClip.getItemAt(0)?.coerceToText(context)?.toString(),
                        label = currentClip.description?.label?.toString(),
                        canVerify = true
                    )
                }
            }.getOrElse {
                ClipboardSnapshot(text = null, label = null, canVerify = false)
            }
        }
    }
}

internal data class ClipboardSnapshot(
    val text: String?,
    val label: String?,
    val canVerify: Boolean
)
