package takagi.ru.monica.ui.common.pull

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.bitwarden.repository.BitwardenRepository
import takagi.ru.monica.util.VibrationPatterns

@Stable
data class PullActionStateHandle(
    val currentOffset: Float,
    val syncHintArmed: Boolean,
    val isBitwardenSyncing: Boolean,
    val showSyncFeedback: Boolean,
    val syncFeedbackMessage: String,
    val syncFeedbackIsSuccess: Boolean,
    val nestedScrollConnection: NestedScrollConnection,
    val onVerticalDrag: (Float) -> Unit,
    val onDragEnd: () -> Unit,
    val onDragCancel: () -> Unit
)

@Composable
fun rememberPullActionState(
    isBitwardenDatabaseView: Boolean,
    isSearchExpanded: Boolean,
    searchTriggerDistance: Float,
    syncTriggerDistance: Float,
    maxDragDistance: Float,
    bitwardenRepository: BitwardenRepository,
    onSearchTriggered: () -> Unit
): PullActionStateHandle {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val onSearchTriggeredState by rememberUpdatedState(onSearchTriggered)
    val syncHoldMillis = 500L

    var currentOffset by remember { mutableFloatStateOf(0f) }
    var hasVibrated by remember { mutableStateOf(false) }
    var hasSyncStageVibrated by remember { mutableStateOf(false) }
    var syncHintArmed by remember { mutableStateOf(false) }
    var isBitwardenSyncing by remember { mutableStateOf(false) }
    var lockPullUntilSyncFinished by remember { mutableStateOf(false) }
    var canRunBitwardenSync by remember { mutableStateOf(false) }
    var showSyncFeedback by remember { mutableStateOf(false) }
    var syncFeedbackMessage by remember { mutableStateOf("") }
    var syncFeedbackIsSuccess by remember { mutableStateOf(false) }

    val vibrator = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        }
    }

    suspend fun resolveSyncableVaultId(): Long? {
        val activeVault = bitwardenRepository.getActiveVault() ?: run {
            canRunBitwardenSync = false
            return null
        }
        val unlocked = bitwardenRepository.isVaultUnlocked(activeVault.id)
        canRunBitwardenSync = unlocked
        return if (unlocked) activeVault.id else null
    }

    fun vibratePullThreshold(isSyncStage: Boolean) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (isSyncStage && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                vibrator?.vibrate(
                    android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_DOUBLE_CLICK)
                )
            } else {
                vibrator?.vibrate(
                    android.os.VibrationEffect.createWaveform(VibrationPatterns.TICK, -1)
                )
            }
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(if (isSyncStage) 36 else 20)
        }
    }

    fun updatePullThresholdHaptics(oldOffset: Float, newOffset: Float) {
        if (oldOffset < searchTriggerDistance && newOffset >= searchTriggerDistance && !hasVibrated) {
            hasVibrated = true
            vibratePullThreshold(isSyncStage = false)
        } else if (newOffset < searchTriggerDistance) {
            hasVibrated = false
        }

        if (!isBitwardenDatabaseView) {
            hasSyncStageVibrated = false
            return
        }

        if (oldOffset < syncTriggerDistance && newOffset >= syncTriggerDistance && !hasSyncStageVibrated) {
            hasSyncStageVibrated = true
            vibratePullThreshold(isSyncStage = true)
        } else if (newOffset < syncTriggerDistance) {
            hasSyncStageVibrated = false
        }
    }

    suspend fun collapsePullOffsetSmoothly() {
        if (currentOffset <= 0.5f) {
            currentOffset = 0f
            return
        }
        Animatable(currentOffset).animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = 180,
                easing = androidx.compose.animation.core.LinearOutSlowInEasing
            )
        ) {
            currentOffset = value
        }
    }

    fun onPullRelease(): Boolean {
        if (isBitwardenDatabaseView && syncHintArmed && !isBitwardenSyncing) {
            syncHintArmed = false
            isBitwardenSyncing = true
            lockPullUntilSyncFinished = true
            currentOffset = syncTriggerDistance
            scope.launch {
                val vaultId = resolveSyncableVaultId()
                if (vaultId == null) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.pull_sync_requires_bitwarden_login),
                        Toast.LENGTH_SHORT
                    ).show()
                    isBitwardenSyncing = false
                    lockPullUntilSyncFinished = false
                    hasVibrated = false
                    hasSyncStageVibrated = false
                    collapsePullOffsetSmoothly()
                    return@launch
                }

                val syncResult = bitwardenRepository.sync(vaultId)
                when (syncResult) {
                    is BitwardenRepository.SyncResult.Success -> {
                        syncFeedbackIsSuccess = true
                        syncFeedbackMessage = context.getString(R.string.pull_sync_success)
                        showSyncFeedback = true
                        Toast.makeText(
                            context,
                            context.getString(R.string.pull_sync_success),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    is BitwardenRepository.SyncResult.Error -> {
                        syncFeedbackIsSuccess = false
                        syncFeedbackMessage = context.getString(R.string.sync_status_failed_full)
                        showSyncFeedback = true
                        Toast.makeText(
                            context,
                            context.getString(R.string.sync_status_failed_full) + ": " + syncResult.message,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    is BitwardenRepository.SyncResult.EmptyVaultBlocked -> {
                        syncFeedbackIsSuccess = false
                        syncFeedbackMessage = context.getString(R.string.sync_status_failed_full)
                        showSyncFeedback = true
                        Toast.makeText(
                            context,
                            context.getString(R.string.sync_status_failed_full) + ": " + syncResult.reason,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                isBitwardenSyncing = false
                lockPullUntilSyncFinished = false
                hasVibrated = false
                hasSyncStageVibrated = false
                collapsePullOffsetSmoothly()
                delay(1400)
                showSyncFeedback = false
            }
            return true
        }

        if (currentOffset >= searchTriggerDistance) {
            onSearchTriggeredState()
            hasVibrated = false
        }
        return false
    }

    fun onVerticalDrag(dragAmount: Float) {
        if (lockPullUntilSyncFinished || dragAmount <= 0f) return
        val newOffset = (currentOffset + dragAmount * 0.5f).coerceAtMost(maxDragDistance)
        val oldOffset = currentOffset
        currentOffset = newOffset
        updatePullThresholdHaptics(oldOffset = oldOffset, newOffset = newOffset)
    }

    val onDragEnd: () -> Unit = {
        scope.launch {
            val syncStarted = onPullRelease()
            if (!syncStarted && !lockPullUntilSyncFinished) {
                collapsePullOffsetSmoothly()
            }
        }
    }

    val onDragCancel: () -> Unit = {
        if (!lockPullUntilSyncFinished) {
            scope.launch { collapsePullOffsetSmoothly() }
        }
    }

    LaunchedEffect(isBitwardenDatabaseView) {
        if (isBitwardenDatabaseView) {
            resolveSyncableVaultId()
        } else {
            canRunBitwardenSync = false
            syncHintArmed = false
            isBitwardenSyncing = false
            lockPullUntilSyncFinished = false
            showSyncFeedback = false
            currentOffset = 0f
            hasVibrated = false
            hasSyncStageVibrated = false
        }
    }

    LaunchedEffect(currentOffset >= syncTriggerDistance, isBitwardenDatabaseView, isBitwardenSyncing) {
        if (isBitwardenDatabaseView && currentOffset >= syncTriggerDistance && !isBitwardenSyncing) {
            resolveSyncableVaultId()
        }
    }

    LaunchedEffect(currentOffset, isBitwardenDatabaseView, canRunBitwardenSync, isBitwardenSyncing) {
        if (isBitwardenDatabaseView && currentOffset >= syncTriggerDistance && canRunBitwardenSync && !isBitwardenSyncing) {
            delay(syncHoldMillis)
            if (isBitwardenDatabaseView && currentOffset >= syncTriggerDistance && canRunBitwardenSync && !isBitwardenSyncing) {
                syncHintArmed = true
            }
        } else {
            syncHintArmed = false
        }
    }

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            currentOffset = 0f
            hasVibrated = false
            hasSyncStageVibrated = false
            syncHintArmed = false
        }
    }

    val nestedScrollConnection = remember(isBitwardenDatabaseView, maxDragDistance) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (lockPullUntilSyncFinished) {
                    return available
                }
                if (currentOffset > 0 && available.y < 0) {
                    val newOffset = (currentOffset + available.y).coerceAtLeast(0f)
                    val consumed = currentOffset - newOffset
                    currentOffset = newOffset
                    return Offset(0f, -consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (lockPullUntilSyncFinished) {
                    return available
                }
                if (available.y > 0 && source == NestedScrollSource.UserInput) {
                    val delta = available.y * 0.5f
                    val newOffset = (currentOffset + delta).coerceAtMost(maxDragDistance)
                    val oldOffset = currentOffset
                    currentOffset = newOffset
                    updatePullThresholdHaptics(oldOffset = oldOffset, newOffset = newOffset)
                    return available
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val syncStarted = onPullRelease()
                if (!syncStarted && !lockPullUntilSyncFinished) {
                    collapsePullOffsetSmoothly()
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (!lockPullUntilSyncFinished && currentOffset > 0f) {
                    val syncStarted = onPullRelease()
                    if (!syncStarted && !lockPullUntilSyncFinished) {
                        collapsePullOffsetSmoothly()
                    }
                }
                return Velocity.Zero
            }
        }
    }

    return PullActionStateHandle(
        currentOffset = currentOffset,
        syncHintArmed = syncHintArmed,
        isBitwardenSyncing = isBitwardenSyncing,
        showSyncFeedback = showSyncFeedback,
        syncFeedbackMessage = syncFeedbackMessage,
        syncFeedbackIsSuccess = syncFeedbackIsSuccess,
        nestedScrollConnection = nestedScrollConnection,
        onVerticalDrag = { dragAmount -> onVerticalDrag(dragAmount) },
        onDragEnd = onDragEnd,
        onDragCancel = onDragCancel
    )
}
