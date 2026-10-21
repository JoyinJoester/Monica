package takagi.ru.monica.lansync

import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.ui.screens.nearbyProgressPercent

class NearbyProgressPercentTest {
    @Test fun unknown_or_stopped_progress_is_not_reported_as_zero_or_success() {
        assertNull(nearbyProgressPercent(DeviceSyncState()))
        assertNull(nearbyProgressPercent(DeviceSyncState(progress = DeviceSyncProgress("preparing"))))
        val transfer = DeviceSyncState(progress = DeviceSyncProgress("transferring", bytes = 50, totalBytes = 100))
        assertNull(nearbyProgressPercent(transfer.copy(phase = "cancelling")))
        assertNull(nearbyProgressPercent(transfer.copy(phase = "cancelled")))
        assertNull(nearbyProgressPercent(transfer.copy(error = "connection_failed")))
        assertEquals(100, nearbyProgressPercent(DeviceSyncState(phase = "completed")))
    }

    @Test fun byte_and_item_progress_are_bounded_and_support_large_counts() {
        assertEquals(25, nearbyProgressPercent(DeviceSyncState(progress = DeviceSyncProgress("preparing", 1, 4))))
        assertEquals(50, nearbyProgressPercent(DeviceSyncState(progress = DeviceSyncProgress("transferring",
            1, 4, Long.MAX_VALUE / 2, Long.MAX_VALUE))))
        assertEquals(100, nearbyProgressPercent(DeviceSyncState(progress = DeviceSyncProgress("preparing", 5, 4))))
        assertEquals(0, nearbyProgressPercent(DeviceSyncState(progress = DeviceSyncProgress("preparing", -1, 4))))
    }
}
