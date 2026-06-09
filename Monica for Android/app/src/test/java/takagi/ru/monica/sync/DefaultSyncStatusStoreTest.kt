package takagi.ru.monica.sync

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DefaultSyncStatusStoreTest {

    @Test
    fun updateObserveSnapshotAndClearUseTheSameKeyedState() = runBlocking {
        val store = DefaultSyncStatusStore()
        val target = SyncTarget.KeePassDatabase(databaseId = 7L)
        val status = SyncTaskStatus(
            key = target.stableKey,
            target = target,
            phase = SyncPhase.QUEUED,
            queuedCount = 1,
            lastTrigger = SyncTrigger.PAGE_VISIBLE
        )

        store.update(status)

        assertEquals(status, store.snapshot()[target.stableKey])
        assertEquals(status, store.observe(target.stableKey).first())

        store.clear(target.stableKey)

        assertFalse(store.snapshot().containsKey(target.stableKey))
    }
}
