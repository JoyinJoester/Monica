package takagi.ru.monica.autofill_ng.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import takagi.ru.monica.R
import takagi.ru.monica.utils.ClipboardUtils

/**
 * Broadcast Receiver for Smart Copy notification actions.
 * 
 * When the user taps the "Copy" notification, this receiver copies the queued value to clipboard.
 */
class SmartCopyReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_COPY = "takagi.ru.monica.ACTION_SMART_COPY"
        const val EXTRA_VALUE = "extra_value"
        const val EXTRA_LABEL = "extra_label"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_COPY) return

        val value = intent.getStringExtra(EXTRA_VALUE) ?: return
        val label = intent.getStringExtra(EXTRA_LABEL) ?: "Credential"

        ClipboardUtils.copyToClipboard(
            context = context,
            text = value,
            label = label,
            sensitive = true
        )

        // Show toast
        val message = if (label.contains("密码") || label.lowercase().contains("password")) {
            context.getString(R.string.password_copied)
        } else {
            context.getString(R.string.username_copied)
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

        // Dismiss the notification
        SmartCopyNotificationHelper.dismissNotification(context)
    }
}



