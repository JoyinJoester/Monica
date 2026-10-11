package takagi.ru.monica.ui.screens

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import takagi.ru.monica.ui.theme.MonicaTheme
import takagi.ru.monica.data.LocalMdbxDatabase
import takagi.ru.monica.data.MdbxEngineType
import takagi.ru.monica.data.MdbxSourceType

/** A real lifecycle-owned composition, including when the test device changes configuration. */
class MdbxSyncOptionsTestActivity : ComponentActivity() {
    var databaseId: Long = 0
        private set
    var manualCalls: Int = 0
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        databaseId = savedInstanceState?.getLong("database")
            ?: (9_900_000L + System.nanoTime() % 100_000L)
        manualCalls = savedInstanceState?.getInt("manualCalls") ?: 0
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MonicaTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                MdbxVaultDetailPage(
                    database = LocalMdbxDatabase(
                        id = databaseId, name = "Sync options", filePath = "/synthetic/sync-options.mdbx",
                        sourceType = MdbxSourceType.REMOTE_WEBDAV.name,
                        engineType = MdbxEngineType.RUST_MDBX2.name
                    ),
                    isDefault = false, conflictCount = 0, diagnostics = null,
                    onSync = { manualCalls++ }, onShowConflicts = {}, onShowHealth = {}, onShowSnapshots = {},
                    onShowCommitHistory = {}, onShowAttachments = {}, onShowMaintenance = {}, onMigrate = null,
                    onSetDefault = {}, onDelete = {}
                )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("database", databaseId)
        outState.putInt("manualCalls", manualCalls)
        super.onSaveInstanceState(outState)
    }
}
