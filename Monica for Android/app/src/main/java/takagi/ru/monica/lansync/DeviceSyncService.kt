package takagi.ru.monica.lansync

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import takagi.ru.monica.MainActivity
import takagi.ru.monica.R
import takagi.ru.monica.security.SessionManager

/** Low-importance connection notification, required by Android for continuous nearby-device sync. */
class DeviceSyncService : Service() {
    override fun onCreate() {
        super.onCreate()
        running = true
        starting = false
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.device_sync_notification_channel), NotificationManager.IMPORTANCE_LOW).apply {
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        })
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.lan_title))
            .setContentText(getString(R.string.device_sync_notification_text))
            .setContentIntent(open).setOngoing(true).setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setCategory(NotificationCompat.CATEGORY_SERVICE).build()
        ServiceCompat.startForeground(this, NOTIFICATION, notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0)
        DeviceSyncCoordinator.get(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!wanted || !SessionManager.isUnlocked.value) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false; starting = false
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        SessionManager.markLocked()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "nearby-device-sync"
        private const val NOTIFICATION = 7419
        private val main = Handler(Looper.getMainLooper())
        private var visible = 0
        private var running = false
        private var starting = false
        private var wanted = false
        private var installed = false

        internal fun install(application: Application) {
            if (installed) return
            installed = true
            application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) { visible++ }
                override fun onActivityStopped(activity: Activity) { visible = (visible - 1).coerceAtLeast(0) }
                override fun onActivityResumed(activity: Activity) {
                    DeviceSyncCoordinator.get(application)
                    if (wanted) startIfVisible(application)
                }
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            })
            DeviceSyncCoordinator.get(application)
        }

        internal fun update(context: Context, state: DeviceSyncState) {
            main.post {
                val next = SessionManager.isUnlocked.value && state.phase != "locked" &&
                    (state.pairing || state.enabled && state.peers.isNotEmpty())
                if (next == wanted) return@post
                wanted = next
                if (wanted) startIfVisible(context)
                // A queued foreground start must reach onCreate/startForeground before it is stopped.
                // Cancelling it here races Android's foreground deadline and can kill the app.
                // onStartCommand observes wanted=false and stops the service after promotion.
                else if (running && !starting) context.stopService(Intent(context, DeviceSyncService::class.java))
            }
        }

        private fun startIfVisible(context: Context) {
            if (running || starting || visible == 0 || !SessionManager.isUnlocked.value) return
            starting = true
            try { ContextCompat.startForegroundService(context, Intent(context, DeviceSyncService::class.java)) }
            catch (_: IllegalStateException) { starting = false }
            catch (_: SecurityException) { starting = false }
        }
    }
}
