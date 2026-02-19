package takagi.ru.monica.autofill

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import takagi.ru.monica.R

/**
 * Autofill 保存通知助手
 * 
 * 由于完全移除了 SaveInfo(避免系统对话框),我们需要一个替代方式来让用户触发保存。
 * 这个类负责显示一个持久通知,当用户填充密码后,点击通知可以保存密码。
 */
object AutofillSaveNotificationHelper {
    
    private const val CHANNEL_ID = "autofill_save_channel"
    private const val NOTIFICATION_ID = 9001
    
    /**
     * 创建通知渠道(Android 8.0+)
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "密码保存提醒"
            val descriptionText = "当您填充密码后,提醒您保存到 Monica"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(true)
                enableVibration(true)
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 显示保存提醒通知
     * 
     * @param context Context
     * @param username 用户名
     * @param password 密码
     * @param website 网站域名
     * @param packageName 应用包名
     */
    fun showSavePromptNotification(
        context: Context,
        username: String,
        password: String,
        website: String?,
        packageName: String
    ) {
        createNotificationChannel(context)
        
        // 创建点击通知时启动的 Intent
        val saveIntent = Intent(context, AutofillSaveTransparentActivity::class.java).apply {
            putExtra(AutofillSaveTransparentActivity.EXTRA_USERNAME, username)
            putExtra(AutofillSaveTransparentActivity.EXTRA_PASSWORD, password)
            putExtra(AutofillSaveTransparentActivity.EXTRA_WEBSITE, website ?: "")
            putExtra(AutofillSaveTransparentActivity.EXTRA_PACKAGE_NAME, packageName)
            putExtra("EXTRA_IS_UPDATE", false)
            putExtra("FROM_NOTIFICATION", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            saveIntent,
            pendingIntentFlags
        )
        
        // 创建取消 Intent
        val dismissIntent = Intent(context, AutofillNotificationDismissReceiver::class.java)
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            dismissIntent,
            pendingIntentFlags
        )
        
        // 构建通知
        val displayText = if (username.isNotEmpty()) {
            "保存 $username 的密码到 Monica?"
        } else {
            "保存密码到 Monica?"
        }
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_key)
            .setContentTitle("保存密码")
            .setContentText(displayText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_key,
                "保存",
                pendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "忽略",
                dismissPendingIntent
            )
            .build()
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
        
        android.util.Log.d("AutofillSaveNotification", "Save prompt notification shown for: $username")
    }
    
    /**
     * 取消保存提醒通知
     */
    fun cancelSavePromptNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
        android.util.Log.d("AutofillSaveNotification", "Save prompt notification cancelled")
    }
}

/**
 * 通知取消接收器
 */
class AutofillNotificationDismissReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AutofillSaveNotificationHelper.cancelSavePromptNotification(context)
        android.util.Log.d("AutofillNotificationDismiss", "User dismissed save notification")
    }
}

