package takagi.ru.monica.autofill_ng.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.Log
import android.widget.Toast
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import takagi.ru.monica.R
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.util.TotpDataResolver
import takagi.ru.monica.util.TotpGenerator
import takagi.ru.monica.utils.AppLauncherIconManager

/**
 * 为"自动填充时通知栏显示验证码"提供支持的前台服务。
 *
 * 特点：
 * - 每秒重新生成一次 OTP，通知内容与倒计时同步刷新
 * - 复制动作总是把**最新**的 OTP 放入剪贴板（不再是通知首次发出时的那一份）
 * - 到达用户设置的展示时长后自动收起通知、停止服务
 * - 使用与既有通知相同的 channel id（保留用户偏好）
 */
class AutofillOtpNotificationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var updateJob: Job? = null
    private val sessionCounter = AtomicLong(0L)

    @Volatile
    private var latestCode: String = ""

    @Volatile
    private var activeSessionId: Long = 0L

    private var isForeground = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_COPY -> handleCopy()
            ACTION_DISMISS -> stopSelfCompletely()
            else -> {
                // 没有可处理的指令且尚未启动 —— 直接停止，避免触发 startForeground 超时
                if (activeSessionId == 0L) stopSelfCompletely()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        updateJob?.cancel()
        scope.cancel()
    }

    // ----- command handlers -----

    private fun handleStart(intent: Intent) {
        val json = intent.getStringExtra(EXTRA_TOTP_JSON)
        val labelArg = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        val duration = intent.getIntExtra(EXTRA_DURATION_SECONDS, DEFAULT_DURATION_SECONDS)
            .coerceAtLeast(1)

        val data = json?.let { payload ->
            TotpDataResolver.parseStoredItemData(itemData = payload)
        }
        if (data == null) {
            Log.w(TAG, "Failed to parse TotpData payload")
            stopSelfCompletely()
            return
        }

        val sessionId = sessionCounter.incrementAndGet()
        activeSessionId = sessionId
        val session = AutofillOtpNotificationSession(
            data = data,
            startedElapsedMs = SystemClock.elapsedRealtime(),
            durationSeconds = duration
        )
        Log.d(
            TAG,
            "start session=$sessionId duration=${duration}s labelLen=${labelArg.length} otpType=${data.otpType} period=${data.period}"
        )

        // 先发一个初始版本以进入前台状态
        val initialSnapshot = session.snapshot(
            nowElapsedMs = SystemClock.elapsedRealtime(),
            nowWallSeconds = System.currentTimeMillis() / 1000L
        )
        latestCode = initialSnapshot.code
        enterForegroundOrUpdate(
            buildNotification(labelArg, initialSnapshot.code, initialSnapshot.remainingSeconds)
        )

        updateJob?.cancel()
        updateJob = scope.launch {
            // 第一轮通知已发，跳过首次 1 秒等待以避免双发
            delay(1000)
            while (isActive) {
                if (activeSessionId != sessionId) {
                    Log.d(TAG, "session=$sessionId became stale; activeSession=$activeSessionId")
                    return@launch
                }
                val now = SystemClock.elapsedRealtime()
                val snapshot = session.snapshot(
                    nowElapsedMs = now,
                    nowWallSeconds = System.currentTimeMillis() / 1000L
                )
                if (snapshot.expired) break

                latestCode = snapshot.code
                runCatching {
                    val notification = buildNotification(labelArg, snapshot.code, snapshot.remainingSeconds)
                    withContext(Dispatchers.Main.immediate) {
                        if (activeSessionId == sessionId) {
                            getSystemService(NotificationManager::class.java)
                                ?.notify(NOTIFICATION_ID, notification)
                        }
                    }
                }.onFailure {
                    Log.w(TAG, "session=$sessionId failed to publish OTP notification tick", it)
                }
                delay(1000)
            }
            stopSelfCompletely(sessionId)
        }
    }

    private fun handleCopy() {
        val code = latestCode
        if (code.isEmpty()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("OTP Code", code))
        // Android 13+ 系统会自带剪贴板提示
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
        }
    }

    // ----- notification helpers -----

    private fun buildNotification(label: String, code: String, remainingSeconds: Int): Notification {
        val formattedCode = formatCodeForDisplay(code)
        val spannable = SpannableString(formattedCode).apply {
            setSpan(RelativeSizeSpan(1.4f), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val copyPendingIntent = PendingIntent.getService(
            this,
            REQUEST_COPY,
            Intent(this, AutofillOtpNotificationService::class.java).setAction(ACTION_COPY),
            pendingIntentFlags()
        )
        val dismissPendingIntent = PendingIntent.getService(
            this,
            REQUEST_DISMISS,
            Intent(this, AutofillOtpNotificationService::class.java).setAction(ACTION_DISMISS),
            pendingIntentFlags()
        )

        val title = if (label.isBlank()) {
            getString(R.string.autofill_otp_notification_channel)
        } else {
            label
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val copyActionText = runCatching {
            getString(R.string.autofill_otp_copy_action, code)
        }.getOrDefault(getString(R.string.copy))

        return builder
            .setSmallIcon(AppLauncherIconManager.resolveBrandingIconRes(this))
            .setContentTitle("$title (${remainingSeconds}s)")
            .setContentText(spannable)
            .setStyle(Notification.BigTextStyle().bigText(spannable))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setShowWhen(false)
            .setDeleteIntent(dismissPendingIntent)
            .addAction(
                Notification.Action.Builder(
                    null,
                    copyActionText,
                    copyPendingIntent
                ).build()
            )
            .build()
    }

    private fun formatCodeForDisplay(code: String): String = when {
        code.length >= 6 -> code.chunked(3).joinToString(" ")
        code.length >= 4 -> code.chunked(2).joinToString(" ")
        else -> code
    }

    private fun enterForegroundOrUpdate(notification: Notification) {
        if (!isForeground) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isForeground = true
        } else {
            getSystemService(NotificationManager::class.java)
                ?.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.autofill_otp_notification_channel),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Shows 2FA codes during autofill"
            setShowBadge(false)
            enableVibration(true)
        }
        nm.createNotificationChannel(channel)
    }

    private fun pendingIntentFlags(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
    }

    private fun stopSelfCompletely(sessionId: Long? = null) {
        if (sessionId != null && sessionId != activeSessionId) {
            Log.d(TAG, "ignore stale stop session=$sessionId activeSession=$activeSessionId")
            return
        }
        Log.d(TAG, "stop session=${sessionId ?: activeSessionId}")
        activeSessionId = 0L
        updateJob?.cancel()
        getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        if (isForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForeground = false
        }
        stopSelf()
    }

    companion object {
        private const val TAG = "AutofillOtpNotifSvc"
        private const val CHANNEL_ID = "autofill_otp"
        private const val NOTIFICATION_ID = 12001
        private const val REQUEST_COPY = 0
        private const val REQUEST_DISMISS = 1
        private const val DEFAULT_DURATION_SECONDS = 30

        const val ACTION_START = "takagi.ru.monica.autofill_ng.ACTION_START_OTP_NOTIF"
        const val ACTION_COPY = "takagi.ru.monica.autofill_ng.ACTION_COPY_OTP_NOTIF"
        const val ACTION_DISMISS = "takagi.ru.monica.autofill_ng.ACTION_DISMISS_OTP_NOTIF"

        const val EXTRA_TOTP_JSON = "totp_json"
        const val EXTRA_LABEL = "label"
        const val EXTRA_DURATION_SECONDS = "duration_seconds"

        /**
         * 启动/刷新 OTP 通知服务。
         *
         * @param totpData 已解密、可直接喂给 [TotpGenerator] 的数据
         * @param label 通知标题（一般是密码条目标题）
         * @param durationSeconds 通知保留的总秒数（到期自动关闭）
         */
        fun start(
            context: Context,
            totpData: TotpData,
            label: String,
            durationSeconds: Int
        ) {
            val payload = runCatching {
                Json.encodeToString(totpData)
            }.onFailure {
                Log.w(TAG, "Unable to serialize TotpData", it)
            }.getOrNull() ?: return

            val intent = Intent(context, AutofillOtpNotificationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TOTP_JSON, payload)
                putExtra(EXTRA_LABEL, label)
                putExtra(EXTRA_DURATION_SECONDS, durationSeconds)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
