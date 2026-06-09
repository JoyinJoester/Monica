package takagi.ru.monica.sync

import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncTaskRunnerTest {

    @Test
    fun requestAndAwaitReturnsBlockValue() = runBlocking {
        val request = SyncRequest(
            requestId = "await-value",
            target = SyncTarget.BitwardenVault(vaultId = 91_001L),
            trigger = SyncTrigger.MANUAL,
            createdAtMillis = 1L
        )

        val result = SyncTaskRunner.requestAndAwait(request) {
            "ok"
        }

        assertTrue(result is SyncTaskAwaitResult.Completed)
        assertEquals("ok", (result as SyncTaskAwaitResult.Completed).value)
    }

    @Test
    fun requestAndAwaitCompletesWhenPendingRequestIsReplaced() = runBlocking {
        val target = SyncTarget.BitwardenVault(vaultId = 91_002L)
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()

        SyncTaskRunner.request(
            request = SyncRequest(
                requestId = "await-replace-first",
                target = target,
                trigger = SyncTrigger.APP_START,
                createdAtMillis = 1L
            )
        ) {
            firstStarted.complete(Unit)
            releaseFirst.await()
        }
        withTimeout(1_000L) { firstStarted.await() }

        val pending = async {
            SyncTaskRunner.requestAndAwait(
                request = SyncRequest(
                    requestId = "await-replace-pending",
                    target = target,
                    trigger = SyncTrigger.PAGE_VISIBLE,
                    createdAtMillis = 2L
                )
            ) {
                "should-not-run"
            }
        }
        delay(20L)

        SyncTaskRunner.request(
            request = SyncRequest(
                requestId = "await-replace-mutation",
                target = target,
                trigger = SyncTrigger.LOCAL_MUTATION,
                createdAtMillis = 3L
            )
        ) {
            Unit
        }

        val result = withTimeout(1_000L) { pending.await() }
        assertTrue(result is SyncTaskAwaitResult.Canceled)

        releaseFirst.complete(Unit)
        delay(50L)
    }

    @Test
    fun requestAndAwaitRechecksNetworkGateWhenQueuedRequestStarts() = runBlocking {
        val target = SyncTarget.KeePassDatabase(databaseId = 91_003L)
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var networkBlocked = false
        var queuedBlockRan = false

        SyncTaskRunner.installNetworkGate(
            SyncNetworkGate { policy ->
                if (networkBlocked && policy == SyncNetworkPolicy.REQUIRED) {
                    SyncError(
                        kind = SyncErrorKind.NETWORK_UNAVAILABLE,
                        redactedMessage = "network_unavailable",
                        retryable = true
                    )
                } else {
                    null
                }
            }
        )

        try {
            SyncTaskRunner.request(
                request = SyncRequest(
                    requestId = "await-network-first",
                    target = target,
                    trigger = SyncTrigger.APP_START,
                    createdAtMillis = 1L
                )
            ) {
                firstStarted.complete(Unit)
                releaseFirst.await()
            }
            withTimeout(1_000L) { firstStarted.await() }

            val pending = async {
                SyncTaskRunner.requestAndAwait(
                    request = SyncRequest(
                        requestId = "await-network-pending",
                        target = target,
                        trigger = SyncTrigger.MANUAL,
                        createdAtMillis = 2L,
                        networkPolicy = SyncNetworkPolicy.REQUIRED
                    )
                ) {
                    queuedBlockRan = true
                    "should-not-run"
                }
            }
            delay(20L)

            networkBlocked = true
            releaseFirst.complete(Unit)

            val result = withTimeout(1_000L) { pending.await() }
            assertTrue(result is SyncTaskAwaitResult.Blocked)
            assertEquals(
                SyncErrorKind.NETWORK_UNAVAILABLE,
                (result as SyncTaskAwaitResult.Blocked).error.kind
            )
            assertEquals(false, queuedBlockRan)
            assertEquals(0, SyncTaskRunner.pendingTaskCountForTest())
        } finally {
            releaseFirst.complete(Unit)
            SyncTaskRunner.installNetworkGate(SyncNetworkGate { null })
            delay(50L)
        }
    }

    @Test
    fun queuedNetworkGateBlockWritesCoreSyncDiagnostic() {
        val runnerSource = projectFile(
            "app/src/main/java/takagi/ru/monica/sync/SyncTaskRunner.kt"
        ).readText()
        val executeBody = runnerSource
            .substringAfter("override suspend fun execute(request: SyncRequest): SyncExecutionResult")
            .substringBefore("private class SyncTaskBlockedException")

        assertTrue(
            "Execution-time network gate blocks must write MonicaSyncDiag blocked logs so real-device traces show blocked instead of looking like a hang.",
            executeBody.contains("networkGate.evaluate(request.networkPolicy)") &&
                executeBody.contains("SyncDiagnostics.blocked(") &&
                executeBody.contains("target = request.target.stableKey.value") &&
                executeBody.contains("trigger = request.trigger.name") &&
                executeBody.contains("policy=\${request.networkPolicy.name}")
        )
    }

    @Test
    fun coordinatorWritesLayeredLifecycleDiagnosticsForPressureLogs() {
        val coordinatorSource = projectFile(
            "app/src/main/java/takagi/ru/monica/sync/DefaultSyncCoordinator.kt"
        ).readText()

        assertTrue(
            "F01 pressure logs need coordinator-layer lifecycle events so nested UI/Worker starts are not mistaken for real concurrent execution.",
            coordinatorSource.contains("layer=coordinator") &&
                coordinatorSource.contains("diagnosticStartedAtMillis") &&
                coordinatorSource.contains("SyncDiagnostics.queued(") &&
                coordinatorSource.contains("SyncDiagnostics.start(") &&
                coordinatorSource.contains("SyncDiagnostics.success(") &&
                coordinatorSource.contains("SyncDiagnostics.failed(") &&
                coordinatorSource.contains("SyncDiagnostics.blocked(") &&
                coordinatorSource.contains("val blockedResult = SyncExecutionResult.Blocked(") &&
                coordinatorSource.contains("logReplacedPendingRequest(") &&
                coordinatorSource.contains("reason = \"replaced\"") &&
                coordinatorSource.contains("logCanceledPendingRequests(") &&
                coordinatorSource.contains("runtime.pendingRequests.clear()") &&
                coordinatorSource.contains("reason = \"merged\"")
        )
    }

    private fun projectFile(relativePath: String): File {
        var dir = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            dir.parentFile != null &&
            !File(dir, "settings.gradle").exists() &&
            !File(dir, "settings.gradle.kts").exists()
        ) {
            dir = dir.parentFile!!.canonicalFile
        }
        return File(dir, relativePath)
    }
}
