package takagi.ru.monica.autofill

import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import takagi.ru.monica.R

class AutofillNotificationReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_COPY_OTP = "takagi.ru.monica.autofill.ACTION_COPY_OTP"
        const val EXTRA_OTP_CODE = "extra_otp_code"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_COPY_OTP) {
            val code = intent.getStringExtra(EXTRA_OTP_CODE) ?: return
            
            // 复制到剪贴板
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = android.content.ClipData.newPlainText("OTP Code", code)
            clipboard.setPrimaryClip(clip)
            
            // 显示Toast提示 (如果是在 Android 13+，系统剪贴板提示可能已足够，但为了保险起见还是显示)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
            }
            
            // 关闭通知栏? 通常点击操作按钮后不会自动关闭通知，除非 PendingIntent 是 Activity 并且调用了 cancel。
            // 这里我们可能希望保留通知以便查看，或者根据需求关闭。
            // "Notification shows 2fa validator" -> Copy usually keeps it or cancels?
            // Usually copying the code fulfills the purpose, so we might want to cancel it.
            // But we need the notificationId.
            // Let's assume user dismisses it manually or timeout handles it.
            
            // To auto-cancel, we would need notification ID in intent.
            val notificationId = intent.getIntExtra("notification_id", -1)
            if (notificationId != -1) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                notificationManager.cancel(notificationId)
            }
        }
    }
}

