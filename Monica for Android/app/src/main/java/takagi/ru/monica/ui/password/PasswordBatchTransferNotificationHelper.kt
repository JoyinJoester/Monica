package takagi.ru.monica.ui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import takagi.ru.monica.MainActivity
import takagi.ru.monica.R
import takagi.ru.monica.ui.components.UnifiedMoveAction

internal object PasswordBatchTransferNotificationHelper {

    private const val CHANNEL_ID = "password_batch_transfer_progress"
    private const val BASE_NOTIFICATION_ID = 45_000

    fun createNotificationId(): Int {
        return BASE_NOTIFICATION_ID + ((System.currentTimeMillis() % 10_000L).toInt())
    }

    fun showProgress(
        context: Context,
        notificationId: Int,
        action: UnifiedMoveAction,
        processed: Int,
        total: Int,
        targetLabel: String
    ) {
        ensureChannel(context)
        if (!canPostNotification(context)) {
            return
        }

        val progressText = context.getString(
            R.string.password_batch_transfer_notification_progress,
            processed.coerceAtLeast(0),
            total.coerceAtLeast(0)
        )
        val contentText = context.getString(
            R.string.password_batch_transfer_target,
            targetLabel
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_key)
            .setContentTitle(notificationTitle(context, action))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$progressText\n$contentText"))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(buildMainActivityIntent(context))

        if (total > 0 && processed > 0) {
            builder.setProgress(total, processed.coerceIn(0, total), false)
                .setSubText(progressText)
        } else {
            builder.setProgress(0, 0, true)
        }

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun showCompleted(
        context: Context,
        notificationId: Int,
        action: UnifiedMoveAction,
        successCount: Int,
        failedCount: Int
    ) {
        ensureChannel(context)
        if (!canPostNotification(context)) {
            return
        }

        val detailText = if (failedCount > 0) {
            context.getString(
                R.string.password_batch_transfer_notification_done_detail_partial,
                successCount.coerceAtLeast(0),
                failedCount.coerceAtLeast(0)
            )
        } else {
            context.getString(
                R.string.password_batch_transfer_notification_done_detail_success,
                successCount.coerceAtLeast(0)
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_key)
            .setContentTitle(completedTitle(context, action))
            .setContentText(detailText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detailText))
            .setPriority(if (failedCount > 0) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(buildMainActivityIntent(context))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
        }
    }

    fun cancel(context: Context, notificationId: Int) {
        try {
            NotificationManagerCompat.from(context).cancel(notificationId)
        } catch (_: SecurityException) {
        }
    }

    private fun notificationTitle(context: Context, action: UnifiedMoveAction): String {
        return when (action) {
            UnifiedMoveAction.COPY -> context.getString(R.string.password_batch_transfer_progress_title_copy)
            UnifiedMoveAction.MOVE -> context.getString(R.string.password_batch_transfer_progress_title_move)
        }
    }

    private fun completedTitle(context: Context, action: UnifiedMoveAction): String {
        return when (action) {
            UnifiedMoveAction.COPY -> context.getString(R.string.password_batch_transfer_notification_done_copy)
            UnifiedMoveAction.MOVE -> context.getString(R.string.password_batch_transfer_notification_done_move)
        }
    }

    private fun canPostNotification(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val managerCompat = NotificationManagerCompat.from(context)
        if (!managerCompat.areNotificationsEnabled()) {
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = manager.getNotificationChannel(CHANNEL_ID)
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
                return false
            }
        }

        return true
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.password_batch_transfer_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.password_batch_transfer_notification_channel_desc)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildMainActivityIntent(context: Context): PendingIntent {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            9001,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
