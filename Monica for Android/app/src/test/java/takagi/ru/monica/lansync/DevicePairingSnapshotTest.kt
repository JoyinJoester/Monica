package takagi.ru.monica.lansync

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DevicePairingSnapshotTest {
    @Test fun enrollmentUsesAnEmptyJournalAndCleansItBeforeTransmission() {
        val directory = File(RuntimeEnvironment.getApplication().cacheDir, "pairing-test").apply { mkdirs() }
        val operations = mutableListOf<String>()
        val snapshot = DevicePairingSnapshot.prepare(directory, "test-device") { request ->
            operations += request.getString("operation")
            when (request.getString("operation")) {
                "replica_open" -> {
                    File(request.getString("path")).writeText("encrypted temporary journal")
                    JSONObject().put("handle", 1L)
                }
                "replica_export" -> {
                    File(request.getString("path")).writeText("encrypted empty snapshot")
                    JSONObject().put("content_key", "synthetic-export-key")
                }
                "replica_close" -> JSONObject()
                else -> error("Enrollment must not scan password sources")
            }
        }
        assertEquals(listOf("replica_open", "replica_export", "replica_close"), operations)
        assertEquals("pairing-only-v1", snapshot.revision)
        assertEquals(listOf("changes.mdbxsync"), directory.listFiles()!!.map { it.name })
    }

    @Test fun failedExportClosesTheTemporaryReplica() {
        val directory = File(RuntimeEnvironment.getApplication().cacheDir, "failed-pairing-test").apply { mkdirs() }
        var closed = false
        try {
            DevicePairingSnapshot.prepare(directory, "test-device") { request ->
                when (request.getString("operation")) {
                    "replica_open" -> {
                        File(request.getString("path")).writeText("encrypted temporary journal")
                        JSONObject().put("handle", 1L)
                    }
                    "replica_export" -> throw LanSyncException("storage_error")
                    else -> { closed = true; JSONObject() }
                }
            }
            fail("Export should fail")
        } catch (_: LanSyncException) { }
        assertTrue(closed)
        assertFalse(File(directory, ".pairing.replica").exists())
    }

    @Test fun exportedDiagnosticsRetainStagesButNeverIncludeScannedContent() {
        DeviceSyncDiagLogger.initialize(RuntimeEnvironment.getApplication())
        DeviceSyncDiagLogger.clear()
        DeviceSyncDiagLogger.failure("preparing", "storage_error", "LanSyncException", 0, "replica_open")
        DeviceSyncDiagLogger.scanner("qr_scan label=lan-pairing event=frame_invalid_candidates candidates=1 payload=must-not-leak")
        val log = DeviceSyncDiagLogger.exportPersistedLogs()
        assertTrue(log.contains("stage=preparing"))
        assertTrue(log.contains("operation=replica_open"))
        assertTrue(log.contains("frame_invalid_candidates"))
        assertFalse(log.contains("must-not-leak"))
    }

    @Test fun unavailablePairingDirectoryReportsStorageOperationBeforeCallingNative() {
        val directory = File(RuntimeEnvironment.getApplication().cacheDir, "pairing-directory-is-file")
        directory.writeText("existing file")
        try {
            DevicePairingSnapshot.prepare(directory, "test-device") {
                error("No native request should run when the pairing directory is unavailable")
            }
            fail("An unavailable directory must fail preparation")
        } catch (failure: LanSyncException) {
            assertEquals("storage_directory_failed", failure.code)
            assertEquals("pairing_directory", failure.operation)
            assertEquals("existing file", directory.readText())
        } finally {
            directory.delete()
        }
    }
}
