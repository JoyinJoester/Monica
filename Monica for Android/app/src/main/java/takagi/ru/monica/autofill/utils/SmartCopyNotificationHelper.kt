package takagi.ru.monica.autofill.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import takagi.ru.monica.R

/**
 * Helper for "Smart Copy" feature.
 * 
 * When the user copies one credential (e.g., username), this helper shows a notification
 * that allows them to quickly copy the other credential (e.g., password) with one tap.
 */
object SmartCopyNotificationHelper {

    private const val CHANNEL_ID = "smart_copy_channel"
    private const val NOTIFICATION_ID = 9527

    /**
     * Copies the first value to clipboard and shows a notification to copy the second value.
     * 
     * @param context Application context
     * @param firstValue The value to copy immediately
     * @param firstLabel Label for the first value (e.g., "Username")
     * @param secondValue The value to copy when notification is tapped
     * @param secondLabel Label for the second value (e.g., "Password")
     */
    fun copyAndQueueNext(
        context: Context,
        firstValue: String,
        firstLabel: String,
        secondValue: String,
        secondLabel: String
    ) {
        // 1. Copy first value to clipboard
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(firstLabel, firstValue)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Mark as sensitive to hide from clipboard preview
            clip.description.extras = android.os.PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        clipboard.setPrimaryClip(clip)

        // 2. Show notification to copy second value
        showCopyNotification(context, secondValue, secondLabel)
    }

    private fun showCopyNotification(context: Context, valueToCopy: String, label: String) {
        createNotificationChannel(context)

        // Create intent for the broadcast receiver that will handle the copy action
        val copyIntent = Intent(context, SmartCopyReceiver::class.java).apply {
            action = SmartCopyReceiver.ACTION_COPY
            putExtra(SmartCopyReceiver.EXTRA_VALUE, valueToCopy)
            putExtra(SmartCopyReceiver.EXTRA_LABEL, label)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID,
            copyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationText = if (label.contains("密码") || label.lowercase().contains("password")) {
            context.getString(R.string.smart_copy_notification_copy_password)
        } else {
            context.getString(R.string.smart_copy_notification_copy_username)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_key)
            .setContentTitle(context.getString(R.string.smart_copy_notification_title))
            .setContentText(notificationText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setTimeoutAfter(60_000) // Auto-dismiss after 60 seconds
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Notification permission not granted
            android.util.Log.w("SmartCopy", "Notification permission not granted", e)
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Smart Copy"
            val descriptionText = "Quick copy for credentials"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Dismisses the Smart Copy notification.
     */
    fun dismissNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}

