package takagi.ru.monica.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import takagi.ru.monica.MainActivity
import takagi.ru.monica.R
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.repository.SecureItemRepository
import takagi.ru.monica.util.TotpGenerator
import takagi.ru.monica.utils.SettingsManager

class NotificationValidatorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var settingsManager: SettingsManager
    private lateinit var secureItemRepository: SecureItemRepository
    private var updateJob: Job? = null

    companion object {
        private const val CHANNEL_ID = "notification_validator_channel"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_COPY = "takagi.ru.monica.service.ACTION_COPY"
        private const val EXTRA_CODE = "extra_code"
    }

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager(this)
        val database = PasswordDatabase.getDatabase(this)
        secureItemRepository = SecureItemRepository(database.secureItemDao())
        createNotificationChannel()
        startObservingSettings()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_COPY) {
            val code = intent.getStringExtra(EXTRA_CODE)
            if (!code.isNullOrEmpty()) {
                copyToClipboard(code)
            }
        }
        return START_STICKY
    }

    private fun copyToClipboard(code: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("TOTP Code", code)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, getString(R.string.generator_copied), Toast.LENGTH_SHORT).show()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun startObservingSettings() {
        serviceScope.launch {
            settingsManager.settingsFlow
                .map { settings -> Pair(settings.notificationValidatorEnabled, settings.notificationValidatorId) }
                .distinctUntilChanged()
                .collectLatest { (enabled, id) ->
                    if (enabled && id != -1L) {
                        startUpdatingNotification(id)
                    } else {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
        }
    }

    private fun startUpdatingNotification(id: Long) {
        updateJob?.cancel()
        updateJob = serviceScope.launch {
            val item = secureItemRepository.getItemById(id)
            if (item == null) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }

            val totpData = try {
                Json.decodeFromString<TotpData>(item.itemData)
            } catch (e: Exception) {
                null
            }

            if (totpData == null) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }

            while (isActive) {
                val code = TotpGenerator.generateOtp(totpData)
                val remaining = TotpGenerator.getRemainingSeconds(totpData.period)
                
                updateNotification(item.title, code, remaining)
                
                delay(1000)
            }
        }
    }

    private fun updateNotification(title: String, code: String, remaining: Int) {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Copy Action Intent
        val copyIntent = Intent(this, NotificationValidatorService::class.java).apply {
            action = ACTION_COPY
            putExtra(EXTRA_CODE, code)
        }
        val copyPendingIntent = PendingIntent.getService(
            this, 1, copyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Format code for better readability (e.g. "123 456")
        val formattedCode = if (code.length == 6) {
            "${code.substring(0, 3)} ${code.substring(3)}"
        } else {
            code
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(formattedCode) // Code as Title (Larger)
            .setContentText("$title ($remaining s)") // Name and Timer as Text
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_copy, getString(R.string.copy), copyPendingIntent) // Copy Button
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Notification Validator",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows the current TOTP code in the notification bar"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
