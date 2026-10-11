package takagi.ru.monica.lansync

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.security.SessionManager
import java.io.File
import java.util.UUID

internal data class DeviceSyncPeerState(val deviceId: String, val name: String, val status: String, val lastSyncedAt: Long)
internal data class DeviceSyncProgress(val stage: String, val completed: Long = 0, val total: Long = 0, val bytes: Long = 0, val totalBytes: Long = 0)
internal data class DeviceSyncState(
    val enabled: Boolean = true, val intervalSeconds: Int = 0, val peers: List<DeviceSyncPeerState> = emptyList(),
    val phase: String = "idle", val pairing: Boolean = false, val code: String = "",
    val invitations: List<String> = emptyList(), val error: String? = null,
    val failureStage: String = "", val connectionAttempts: Int = 0, val failureKind: String = "",
    val failureOperation: String = "", val progress: DeviceSyncProgress? = null, val failureRecordName: String = "",
    val mode: DeviceSyncMode = DeviceSyncMode.BIDIRECTIONAL,
    val passkeyReview: DevicePasskeySyncReview? = null
)

/** App-owned coordinator. Leaving settings does not cancel remembered-device synchronization. */
internal class DeviceSyncCoordinator private constructor(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Mutex()
    private val security by lazy { SecurityManager(app) }
    private val store by lazy { DeviceSyncPreferenceStore(app, security) }
    private val dataProvider = lazy { DevicePasswordSyncProvider(app, security, store).also { provider -> provider.onProgress = { updateProgress(it) } } }
    private val data by dataProvider
    private val root = File(app.cacheDir, "device-sync/${UUID.randomUUID()}")
    private val mutableState = MutableStateFlow(DeviceSyncState())
    val state = mutableState.asStateFlow()
    private var preferences: DeviceSyncPreferences? = null
    private var snapshot: DevicePasswordExport? = null
    private val snapshots = mutableSetOf<File>()
    private val transfers = mutableMapOf<String, Transfer>()
    private val retiring = mutableMapOf<Long, Transfer>()
    private val attempts = mutableMapOf<String, Long>()
    private val retry = mutableMapOf<String, Pair<Int, Long>>()
    private val statuses = mutableMapOf<String, String>()
    private val force = mutableSetOf<String>()
    private var pairing = false
    private var paused = true
    private var phase = "idle"
    private var code = ""
    private var invitations = emptyList<String>()
    private var error: String? = null
    private var failureStage = ""
    private var connectionAttempts = 0
    private var failureKind = ""
    private var failureOperation = ""
    private var failureRecordName = ""
    private var loadAfter = 0L
    private var running: Job? = null
    private var multicast: WifiManager.MulticastLock? = null
    private var multicastAttempted = false
    private var dataProgress: DeviceSyncProgress? = null
    private val activeHandles = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()
    private val receiveOnlyRevision = "receive-only-v1"
    private val passkeyConsent = DevicePasskeySyncConsent()

    init { DeviceSyncDiagLogger.initialize(app) }

    private class Transfer(val handle: Long, val directory: File, val snapshot: DevicePasswordExport,
        val enrollment: Boolean, val advertisement: String) {
        var applyAttempted = false
        var peer: JSONObject? = null
        var stage = "connecting"
        var attempts = 0
        var remoteAddress = ""
    }

    fun start() {
        if (running != null) return
        scope.launch {
            SessionManager.isUnlocked.collect { unlocked ->
                if (!unlocked) activeHandles.forEach { handle -> runCatching { request("cancel", handle) } }
            }
        }
        running = scope.launch {
            SessionManager.isUnlocked.collectLatest { unlocked ->
                if (!unlocked) {
                    gate.withLock { pause(locked = true); publish() }
                } else {
                    try {
                        while (currentCoroutineContext().isActive) {
                            gate.withLock { tick() }
                            delay(500)
                        }
                    } finally {
                        withContext(NonCancellable) { gate.withLock { pause(locked = true); publish() } }
                    }
                }
            }
        }
    }

    private fun publish() {
        val settings = preferences
        mutableState.value = DeviceSyncState(settings?.enabled ?: state.value.enabled,
            settings?.intervalSeconds ?: state.value.intervalSeconds,
            settings?.peers?.map {
                val cached = statuses[it.deviceId] ?: "offline"
                val status = if (error != null && failureStage == "preparing") "preparation_failed"
                    else if (it.lastSyncedAt == 0L && cached in listOf("offline", "waiting")) "first_sync_pending" else cached
                DeviceSyncPeerState(it.deviceId, it.name, status, it.lastSyncedAt)
            } ?: state.value.peers,
            phase, pairing, code, invitations, error, failureStage, connectionAttempts, failureKind, failureOperation, dataProgress, failureRecordName,
            settings?.mode ?: state.value.mode, passkeyConsent.pending)
        DeviceSyncService.update(app, mutableState.value)
    }

    private fun updateProgress(progress: DeviceSyncProgress) { dataProgress = progress; phase = progress.stage; publish() }

    private fun settings(): DeviceSyncPreferences {
        if (SessionManager.isSessionExpired()) {
            if (SessionManager.isUnlocked.value) SessionManager.markLocked()
            throw CancellationException("device_sync_locked")
        }
        check(SessionManager.isUnlocked.value) { "device_sync_locked" }
        return preferences ?: store.load().also { loaded ->
            preferences = loaded
            if (phase == "locked") phase = "idle"
            loaded.peers.forEach { force += it.deviceId }
        }
    }

    private fun action(block: suspend () -> Unit) {
        start()
        scope.launch {
            gate.withLock {
                try { if (SessionManager.isUnlocked.value) block() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { recordFailure(failure, if (pairing) phase else "preparing") }
                finally { publish() }
            }
        }
    }

    fun setInterval(seconds: Int) = action {
        settings().setInterval(seconds)
        settings().peers.forEach { force += it.deviceId }
        store.save(settings())
    }

    fun setEnabled(enabled: Boolean) = action {
        settings().enabled = enabled
        store.save(settings())
        if (enabled) { phase = "idle"; settings().peers.forEach { force += it.deviceId } } else pause(locked = false)
    }

    fun syncNow() = action {
        settings().syncRequest = Math.addExact(settings().syncRequest, 1)
        settings().peers.forEach { force += it.deviceId; retry.remove(it.deviceId) }
        store.save(settings())
    }

    fun confirmPasskeySync(review: DevicePasskeySyncReview) = action {
        passkeyConsent.confirm(review)
        if (passkeyConsent.pending == null) phase = "idle"
    }

    fun cancelPasskeySync() = setEnabled(false)

    fun setMode(mode: DeviceSyncMode) = action {
        val settings = settings()
        if (settings.mode == mode) return@action
        if (pairing) throw LanSyncException("device_sync_pairing_busy")
        val nextRequest = Math.addExact(settings.syncRequest, 1)
        transfers.values.toList().forEach { stop(it) }; transfers.clear()
        passkeyConsent.clearPending()
        settings.mode = mode; settings.syncRequest = nextRequest
        snapshot = null; dataProgress = null; phase = "idle"; error = null; loadAfter = 0
        failureStage = ""; failureKind = ""; failureOperation = ""; failureRecordName = ""; connectionAttempts = 0
        settings.peers.forEach { force += it.deviceId; retry.remove(it.deviceId) }
        store.save(settings)
        cleanSnapshots()
    }

    fun forget(deviceId: String) = action {
        passkeyConsent.forget(deviceId)
        transfers.remove(deviceId)?.let { stop(it) }
        settings().peers.removeAll { it.deviceId == deviceId }
        force.remove(deviceId); retry.remove(deviceId); statuses.remove(deviceId)
        store.save(settings())
        cleanSnapshots()
    }

    fun pair(invitation: String? = null) = action {
        if (pairing) return@action
        DeviceSyncDiagLogger.event(if (invitation == null) "show_pairing_qr" else "connect_from_invitation")
        phase = "preparing"; failureStage = ""; failureKind = ""; failureOperation = ""; connectionAttempts = 0
        if (invitation != null && LanPairingInvitation.parse(invitation)?.version != 2) throw LanSyncException("unsupported_protocol")
        if (settings().peers.size >= 8) throw LanSyncException("too_many_devices")
        store.save(settings())
        transfers.values.toList().forEach { stop(it) }; transfers.clear()
        pairing = true; paused = false; phase = "preparing"; error = null
        acquireNetwork()
        publish()
        try {
            val directory = File(root, "out-${UUID.randomUUID()}").also { snapshots += it }
            val outgoing = DevicePairingSnapshot.prepare(directory, settings().deviceId)
            transfers["pairing"] = startTransfer(outgoing, null, invitation, true)
        }
        catch (failure: Exception) {
            pairing = false; code = ""; invitations = emptyList(); phase = "failed"
            throw failure
        }
    }

    fun cancelPairing() = action {
        transfers.remove("pairing")?.let { stop(it) }
        pairing = false; phase = "idle"; code = ""; invitations = emptyList()
    }

    private fun advertisement(revision: String): String = settings().let {
        "$revision:${it.settingsVersion}:${it.settingsWriter}:${it.syncRequest}"
    }

    private fun request(operation: String, handle: Long): JSONObject =
        RustLanSync.request(JSONObject().put("operation", operation).put("handle", handle))

    private suspend fun currentSnapshot(): DevicePasswordExport {
        if (failureStage == "preparing") {
            error = null; failureStage = ""; failureKind = ""; failureOperation = ""; failureRecordName = ""
        }
        try {
            val receiveOnly = settings().mode == DeviceSyncMode.RECEIVE_ONLY
            val revision = if (receiveOnly) receiveOnlyRevision else preparingDeviceSync("vault_revision") { data.revision() }
            val issues = if (receiveOnly) emptyList() else data.currentPasskeyIssues()
            snapshot?.takeIf { it.revision == revision && it.passkeyIssues == issues }?.let { return it }
            val directory = File(root, "out-${UUID.randomUUID()}").also { snapshots += it }
            snapshot = if (receiveOnly) {
                check(directory.mkdirs() || directory.isDirectory)
                DevicePasswordExport(revision, directory, "")
            } else preparingDeviceSync("vault_export") { data.prepare(directory) }
            return requireNotNull(snapshot)
        } finally {
            cleanSnapshots()
            if (dataProgress?.stage == "preparing") { dataProgress = null; if (phase == "preparing") phase = "idle" }
        }
    }

    private suspend fun startTransfer(outgoing: DevicePasswordExport, peer: SavedSyncPeer?, invitation: String?, enrollment: Boolean): Transfer {
        currentCoroutineContext().ensureActive()
        check(SessionManager.isUnlocked.value) { "device_sync_locked" }
        val directory = File(root, "in-${UUID.randomUUID()}")
        val settings = settings()
        val advertisement = advertisement(outgoing.revision)
        val device = JSONObject().put("device_id", settings.deviceId).put("name", Build.MODEL.take(64))
            .put("pair_id", peer?.pairId.orEmpty()).put("interval_seconds", settings.intervalSeconds)
            .put("settings_version", settings.settingsVersion).put("settings_writer", settings.settingsWriter)
            .put("sync_request", settings.syncRequest).put("revision", outgoing.revision).put("content_type", "vault").put("vault_content_version", 2)
            .put("sync_mode", (if (enrollment) DeviceSyncMode.BIDIRECTIONAL else settings.mode).wireName)
        val parameters = JSONObject().put("operation", if (invitation == null) "start" else "connect").put("protocol", 2)
            .put("name", Build.MODEL.take(64)).put("device", device).put("revision", advertisement)
            .put("vault_id", "monica-passwords-v1").put("content_key", outgoing.contentKey)
            .put("outgoing_directory", if (!enrollment && settings.mode == DeviceSyncMode.RECEIVE_ONLY) JSONObject.NULL else outgoing.directory.absolutePath)
            .put("incoming_directory", directory.absolutePath)
        invitation?.let { parameters.put("invitation", it) }
        peer?.let {
            parameters.put("expected_device_id", it.deviceId)
            if (invitation == null) parameters.put("trusted_key", it.trustedKey)
        }
        val started = try { RustLanSync.request(parameters) } catch (failure: Exception) { deleteOwned(directory); throw failure }
        val transfer = Transfer(started.getLong("handle"), directory, outgoing, enrollment, advertisement)
        activeHandles += transfer.handle
        if (!SessionManager.isUnlocked.value || !currentCoroutineContext().isActive) {
            withContext(NonCancellable) { stop(transfer) }
            throw CancellationException("device_sync_locked")
        }
        if (enrollment) {
            code = started.optString("code")
            val links = started.optJSONArray("invitations")
            invitations = links?.let { (0 until it.length()).map(it::getString) }.orEmpty()
            phase = if (invitation == null) "waiting" else "connecting"
            DeviceSyncDiagLogger.event(if (invitation == null) "pairing_qr_ready" else "pairing_connection_started")
            publish()
        }
        return transfer
    }

    private suspend fun tick() {
        try {
            if ((app.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager)?.isKeyguardLocked == true) {
                if (!paused || preferences != null) pause(locked = true)
                return
            }
            retiring.values.toList().forEach { stop(it) }
            cleanSnapshots()
            if (SystemClock.elapsedRealtime() < loadAfter) return
            val settings = settings()
            if (!settings.enabled && !pairing) {
                if (!paused) pause(locked = false)
                return
            }
            if (!pairing && settings.peers.isEmpty()) { if (!paused) pause(locked = false); return }
            paused = false
            acquireNetwork()
            transfers.toMap().forEach { (id, transfer) ->
                try { poll(id, transfer) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    recordFailure(failure, transfer.stage, transfer.attempts)
                    fail(id, transfer.enrollment)
                    stop(transfer); transfers.remove(id)
                }
            }
            if (!pairing && settings.peers.isNotEmpty() && retiring.isEmpty()) maintainConnections()
            cleanSnapshots()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            recordFailure(failure, "preparing")
            loadAfter = SystemClock.elapsedRealtime() + 10_000
        } finally { publish() }
    }

    private suspend fun maintainConnections() {
        val outgoing = currentSnapshot()
        var discovery: JSONObject? = null
        val settings = settings()
        // Pairing exchanges no vault data. Every actual send, including the first
        // automatic send after enrollment, must pass this review before JNI starts.
        if (passkeyConsent.review(outgoing.passkeyIssues, settings.peers.map { it.deviceId }.toSet()) != null) {
            phase = "passkey_review"; dataProgress = null
            return
        }
        for (peer in settings.peers.toList()) {
            currentCoroutineContext().ensureActive()
            if (peer.deviceId in transfers || (retry[peer.deviceId]?.second ?: 0) > SystemClock.elapsedRealtime()) continue
            if (settings.deviceId > peer.deviceId) {
                transfers[peer.deviceId] = startTransfer(outgoing, peer, null, false)
                statuses[peer.deviceId] = "waiting"
                continue
            }
            if (discovery == null) discovery = RustLanSync.request(JSONObject().put("operation", "discover")
                .put("targets", org.json.JSONArray(settings.peers.map { it.address }.filter(String::isNotEmpty).distinct().take(64))))
            currentCoroutineContext().ensureActive()
            val devices = discovery.getJSONArray("devices")
            val found = (0 until devices.length()).map(devices::getJSONObject).firstOrNull {
                it.optString("device_id") == peer.deviceId && it.optString("session") == peer.pairId && it.optBoolean("trusted")
            }
            if (found == null) { statuses[peer.deviceId] = "offline"; force += peer.deviceId; continue }
            val parts = found.optString("revision").split(':', limit = 4)
            val changed = outgoing.revision != peer.lastSentRevision || parts.firstOrNull() != peer.lastReceivedRevision
            val version = parts.getOrNull(1)?.toLongOrNull()
            val writer = parts.getOrNull(2).orEmpty()
            val policyChanged = version != null && (version > settings.settingsVersion || version == settings.settingsVersion && writer > settings.settingsWriter)
            val requested = parts.getOrNull(3)?.toLongOrNull()?.let { it != peer.lastReceivedRequest } ?: false
            val elapsed = attempts[peer.deviceId]?.let { SystemClock.elapsedRealtime() - it } ?: Long.MAX_VALUE
            if (!DeviceSyncCadence.isDue(settings.intervalSeconds, elapsed, changed, policyChanged || requested || peer.deviceId in force)) {
                statuses[peer.deviceId] = if (changed) "scheduled" else "online"; continue
            }
            val alternatives = found.optJSONArray("addresses")
            val candidates = (listOf(found.getString("address")) + alternatives?.let { (0 until it.length()).map(it::getString) }.orEmpty())
                .distinct().take(8).joinToString(",")
            val link = "monica-lan:2:$candidates:${found.getInt("port")}:${peer.trustedKey}"
            transfers[peer.deviceId] = startTransfer(outgoing, peer, link, false)
            attempts[peer.deviceId] = SystemClock.elapsedRealtime()
            statuses[peer.deviceId] = "syncing"
        }
    }

    private suspend fun poll(id: String, transfer: Transfer) {
        val progress = request("progress", transfer.handle)
        val next = progress.getString("phase")
        if(transfer.enrollment || next != "waiting") phase = next else if(!pairing) phase = "idle"
        if (next in listOf("sending", "receiving", "transferring")) updateProgress(DeviceSyncProgress(next, bytes = progress.optLong("transferred"), totalBytes = progress.optLong("total")))
        transfer.stage = if (next == "failed") progress.optString("failed_at", transfer.stage) else next
        transfer.attempts = progress.optInt("connection_attempt")
        transfer.remoteAddress = progress.optString("remote_address", transfer.remoteAddress)
        if (transfer.enrollment) phase = next else if (next != "waiting") statuses[id] = "syncing"
        if (next == "waiting" && !transfer.enrollment) {
            val revision = if (settings().mode == DeviceSyncMode.RECEIVE_ONLY) receiveOnlyRevision else data.revision()
            if (advertisement(revision) != transfer.advertisement) { stop(transfer); transfers.remove(id) }
            return
        }
        if (next == "received" && !transfer.applyAttempted) {
            val remote = progress.getJSONObject("peer")
            val pairingOnly = transfer.enrollment && remote.optString("revision") == DevicePairingSnapshot.REVISION
            val receiveContent = !pairingOnly && settings().mode != DeviceSyncMode.SEND_ONLY && remote.optBoolean("receive_content", true)
            if (receiveContent && DeviceSyncEditGate.isEditing) return
            transfer.applyAttempted = true
            transfer.stage = "applying"
            transfer.peer = remote
            try {
                currentCoroutineContext().ensureActive()
                if (remote.getString("content_type") != "vault" || remote.optInt("vault_content_version") != 2) throw LanSyncException("unsupported_protocol")
                if (remote.getString("device_id") == settings().deviceId) throw LanSyncException("same_device")
                if (receiveContent) data.apply(transfer.directory, remote)
                val settings = settings()
                settings.acceptInterval(remote.getInt("interval_seconds"), remote.getLong("settings_version"), remote.getString("settings_writer"))
                if (transfer.enrollment) {
                    val secrets = remote.getJSONObject("pairing")
                    val peer = SavedSyncPeer(remote.getString("device_id"), remote.getString("name"), secrets.getString("pair_id"), secrets.getString("trusted_key"))
                    peer.address = transfer.remoteAddress
                    settings.peers.removeAll { it.deviceId == peer.deviceId }; settings.peers += peer
                    settings.enabled = true
                    force += peer.deviceId
                }
                transfer.stage = "remembering"
                store.save(settings)
                currentCoroutineContext().ensureActive()
                check(SessionManager.isUnlocked.value) { "device_sync_locked" }
                RustLanSync.request(JSONObject().put("operation", "acknowledge").put("handle", transfer.handle).put("success", true))
            } catch (failure: Exception) {
                runCatching { RustLanSync.request(JSONObject().put("operation", "acknowledge").put("handle", transfer.handle).put("success", false)) }
                throw failure
            }
        }
        if (next !in listOf("completed", "failed", "cancelled")) return
        if (next == "completed") {
            phase = "completed"; dataProgress = null
            val remote = requireNotNull(transfer.peer)
            val remoteId = remote.getString("device_id")
            settings().peers.firstOrNull { it.deviceId == remoteId }?.let {
                val contentSynced = transfer.snapshot.revision != DevicePairingSnapshot.REVISION
                if (contentSynced) {
                    it.lastSyncedAt = System.currentTimeMillis()
                    it.lastSentRevision = transfer.snapshot.revision
                    it.lastReceivedRevision = remote.getString("revision")
                    it.lastReceivedRequest = remote.optLong("sync_request")
                }
                if (transfer.remoteAddress.isNotEmpty()) it.address = transfer.remoteAddress
                statuses[remoteId] = if (contentSynced) "online" else "waiting"; retry.remove(remoteId)
                if (!transfer.enrollment) force.remove(remoteId)
            }
            store.save(settings())
            error = null
        } else {
            fail(id, transfer.enrollment)
            recordFailure(LanSyncException(progress.optString("error", "device_sync_failed")), transfer.stage, transfer.attempts)
        }
        if (transfer.enrollment) { pairing = false; code = ""; invitations = emptyList(); phase = next }
        stop(transfer); transfers.remove(id)
    }

    private fun fail(id: String, enrollment: Boolean) {
        statuses[id] = "offline"; force += id
        val count = minOf(6, (retry[id]?.first ?: 0) + 1)
        retry[id] = count to (SystemClock.elapsedRealtime() + minOf(60, 1 shl count) * 1000L)
        if (enrollment) { pairing = false; code = ""; invitations = emptyList(); phase = "failed" }
    }

    private fun recordFailure(failure: Exception, stage: String, attempts: Int = 0) {
        if (stage == "preparing") passkeyConsent.clearPending()
        error = errorCode(failure)
        failureStage = stage
        failureKind = (failure as? LanSyncException)?.failureKind?.ifEmpty { null } ?: failure.javaClass.simpleName
        failureOperation = (failure as? LanSyncException)?.operation.orEmpty()
        failureRecordName = (failure as? LanSyncException)?.recordName.orEmpty()
        phase = "failed"; dataProgress = null
        connectionAttempts = attempts
        DeviceSyncDiagLogger.failure(stage, error.orEmpty(), failureKind, attempts, failureOperation)
    }

    private suspend fun pause(locked: Boolean) {
        withContext(NonCancellable) {
            transfers.values.toList().forEach { stop(it) }; transfers.clear()
            retiring.values.toList().forEach { stop(it) }
            if (!paused && dataProvider.isInitialized()) data.pause()
            snapshot = null; cleanSnapshots()
            passkeyConsent.clearPending()
            pairing = false; code = ""; invitations = emptyList(); phase = if (locked) "locked" else "paused"
            preferences?.peers?.forEach { statuses[it.deviceId] = phase; force += it.deviceId }
            publish()
            if (locked) preferences = null
            multicast?.let { if (it.isHeld) it.release() }; multicast = null
            multicastAttempted = false
            paused = true
        }
    }

    private suspend fun stop(transfer: Transfer) {
        retiring[transfer.handle] = transfer
        try {
            request("cancel", transfer.handle)
            repeat(20) {
                if (request("progress", transfer.handle).getString("phase") in listOf("completed", "failed", "cancelled")) {
                    request("release", transfer.handle)
                    activeHandles -= transfer.handle
                    retiring.remove(transfer.handle)
                    deleteOwned(transfer.directory)
                    return
                }
                delay(50)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { }
    }

    private fun cleanSnapshots() {
        val used = (transfers.values + retiring.values).map { it.snapshot.directory }.toMutableSet()
        snapshot?.let { used += it.directory }
        snapshots.filter { it !in used }.forEach { if (deleteOwned(it)) snapshots.remove(it) }
    }

    private fun deleteOwned(directory: File): Boolean {
        return try {
            if (directory.canonicalFile.parentFile != root.canonicalFile) return false
            !directory.exists() || directory.deleteRecursively()
        } catch (_: Exception) { false }
    }

    private fun acquireNetwork() {
        if (multicastAttempted) return
        multicastAttempted = true
        try {
            multicast = (app.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
                ?.createMulticastLock("monica-device-sync")?.apply { setReferenceCounted(false); acquire() }
        } catch (failure: Exception) {
            // Direct QR connections and remembered-address discovery do not require multicast.
            DeviceSyncDiagLogger.failure("network", "multicast_unavailable", failure.javaClass.simpleName, 0)
        }
    }

    companion object {
        @Volatile private var instance: DeviceSyncCoordinator? = null
        fun get(context: Context): DeviceSyncCoordinator = instance ?: synchronized(this) {
            instance ?: DeviceSyncCoordinator(context).also { instance = it; it.start() }
        }
        private fun errorCode(error: Exception): String = (error as? LanSyncException)?.code ?: "device_sync_failed"
    }
}
