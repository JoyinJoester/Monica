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
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.json.Json
import takagi.ru.monica.MainActivity
import takagi.ru.monica.R
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.repository.SecureItemRepository
import takagi.ru.monica.util.TotpGenerator
import takagi.ru.monica.utils.SettingsManager

class NotificationValidatorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var settingsManager: SettingsManager
    private lateinit var secureItemRepository: SecureItemRepository
    private var updateJob: Job? = null
    private var currentState: ValidatorState = ValidatorState.Idle
    private var hasStartedForeground = false
    @Volatile private var latestIndex: TotpIndex? = null
    @Volatile private var latestSettings: AppSettings? = null

    companion object {
        private const val CHANNEL_ID = "notification_validator_channel"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_COPY = "takagi.ru.monica.service.ACTION_COPY"
        private const val ACTION_SWITCH = "takagi.ru.monica.service.ACTION_SWITCH"
        private const val EXTRA_CODE = "extra_code"
    }

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager(this)
        val database = PasswordDatabase.getDatabase(this)
        secureItemRepository = SecureItemRepository(database.secureItemDao())
        createNotificationChannel()
        startPlaceholderNotification()
        startObserving()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_COPY -> {
                val code = intent.getStringExtra(EXTRA_CODE)
                if (!code.isNullOrEmpty()) {
                    copyToClipboard(code)
                }
            }
            ACTION_SWITCH -> handleSwitchRequest()
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
        updateJob?.cancel()
        serviceScope.cancel()
    }

    private fun startObserving() {
        val totpIndexFlow = secureItemRepository.getItemsByType(ItemType.TOTP)
            .map { items ->
                val entries = items.mapNotNull { item ->
                    runCatching { Json.decodeFromString<TotpData>(item.itemData) }
                        .getOrNull()
                        ?.let { data ->
                            TotpEntry(
                                id = item.id,
                                title = item.title,
                                data = data
                            )
                        }
                }

                TotpIndex(entries = entries)
            }

        serviceScope.launch {
            combine(
                settingsManager.settingsFlow,
                totpIndexFlow
            ) { settings, index ->
                settings to index
            }.collectLatest { (settings, index) ->
                latestSettings = settings
                latestIndex = index

                if (!settings.notificationValidatorEnabled) {
                    cancelActiveJobs()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    hasStartedForeground = false
                    currentState = ValidatorState.Idle
                    stopSelf()
                    return@collectLatest
                }

                val targetId = settings.notificationValidatorId.takeIf { it != -1L }
                if (targetId != null) {
                    startUpdatingNotification(targetId, index)
                } else {
                    showNoMatchPlaceholder()
                }
            }
        }
    }

    private fun handleSwitchRequest() {
        val index = latestIndex ?: return
        val entries = index.entries
        if (entries.isEmpty()) return

        val sorted = entries.sortedBy { it.title.lowercase() }
        val currentId = (currentState as? ValidatorState.Showing)?.id
            ?: latestSettings?.notificationValidatorId
            ?: -1L

        val nextEntry = when (val pos = sorted.indexOfFirst { it.id == currentId }) {
            -1 -> sorted.first()
            sorted.lastIndex -> sorted.first()
            else -> sorted[pos + 1]
        }

        serviceScope.launch {
            settingsManager.updateNotificationValidatorId(nextEntry.id)
        }
        startUpdatingNotification(nextEntry.id, index)
    }

    private fun startUpdatingNotification(id: Long, index: TotpIndex) {
        if (currentState is ValidatorState.Showing && (currentState as ValidatorState.Showing).id == id && updateJob?.isActive == true) return

        updateJob?.cancel()
        currentState = ValidatorState.Showing(id)

        val entry = index.entries.firstOrNull { it.id == id }
        if (entry == null) {
            currentState = ValidatorState.Idle
            return
        }

        updateJob = serviceScope.launch {
            while (isActive) {
                val code = TotpGenerator.generateOtp(entry.data)
                val remaining = TotpGenerator.getRemainingSeconds(entry.data.period)
                updateNotification(entry.title, code, remaining)
                delay(1000)
            }
        }
    }

    private suspend fun ensureForeground(notification: android.app.Notification) {
        withContext(Dispatchers.Main.immediate) {
            if (!hasStartedForeground) {
                startForeground(NOTIFICATION_ID, notification)
                hasStartedForeground = true
            } else {
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private suspend fun updateNotification(title: String, code: String, remaining: Int) {
        val notificationIntent = Intent(this@NotificationValidatorService, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this@NotificationValidatorService, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val copyIntent = Intent(this@NotificationValidatorService, NotificationValidatorService::class.java).apply {
            action = ACTION_COPY
            putExtra(EXTRA_CODE, code)
        }
        val copyPendingIntent = PendingIntent.getService(
            this@NotificationValidatorService, 1, copyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedCode = if (code.length == 6) {
            "${code.substring(0, 3)} ${code.substring(3)}"
        } else {
            code
        }

        val spannableCode = SpannableString(formattedCode)
        spannableCode.setSpan(RelativeSizeSpan(1.5f), 0, spannableCode.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannableCode.setSpan(StyleSpan(Typeface.BOLD), 0, spannableCode.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        val switchIntent = Intent(this@NotificationValidatorService, NotificationValidatorService::class.java).apply {
            action = ACTION_SWITCH
        }
        val switchPendingIntent = PendingIntent.getService(
            this@NotificationValidatorService, 2, switchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this@NotificationValidatorService, CHANNEL_ID)
            .setContentTitle("$title ($remaining s)")
            .setContentText(spannableCode)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_copy, getString(R.string.copy), copyPendingIntent)
            .addAction(R.drawable.ic_copy, getString(R.string.notification_validator_switch), switchPendingIntent)
            .build()

        ensureForeground(notification)
    }

    private fun startPlaceholderNotification() {
        serviceScope.launch {
            val notification = NotificationCompat.Builder(this@NotificationValidatorService, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_validator_ready))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build()

            ensureForeground(notification)
            currentState = ValidatorState.Idle
        }
    }

    private fun showNoMatchPlaceholder() {
        if (currentState is ValidatorState.NoMatch) return
        updateJob?.cancel()
        currentState = ValidatorState.NoMatch

        serviceScope.launch {
            val notification = NotificationCompat.Builder(this@NotificationValidatorService, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_validator_no_match))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build()

            ensureForeground(notification)
        }
    }

    private fun cancelActiveJobs() {
        updateJob?.cancel()
        currentState = ValidatorState.Idle
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

private data class TotpEntry(
    val id: Long,
    val title: String,
    val data: TotpData
)

private data class TotpIndex(
    val entries: List<TotpEntry>
)

private sealed interface ValidatorState {
    object Idle : ValidatorState
    object NoMatch : ValidatorState
    data class Showing(val id: Long) : ValidatorState
}
