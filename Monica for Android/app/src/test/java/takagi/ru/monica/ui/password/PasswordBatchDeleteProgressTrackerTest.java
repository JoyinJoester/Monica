package takagi.ru.monica.ui.password;

import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class PasswordBatchDeleteProgressTrackerTest {
    private final PasswordBatchDeleteProgressTracker tracker = PasswordBatchDeleteProgressTracker.INSTANCE;

    @After
    public void clearProgress() { tracker.clear(); }

    @Test
    public void partialDeletionKeepsTheOriginalTotalAndReportsFailure() {
        tracker.update(0, 10);
        tracker.update(10, 10);
        tracker.complete(6);
        PasswordBatchDeleteGlobalProgressState state = tracker.getProgress().getValue();
        assertNotNull(state);
        assertEquals(10, state.getTotal());
        assertEquals(Integer.valueOf(6), state.getSuccessCount());
        assertNotEquals(PasswordBatchDeletePhase.SUCCESS, state.getPhase());
    }

    @Test
    public void completeFailureRemainsVisible() {
        tracker.update(0, 4);
        tracker.update(4, 4);
        tracker.complete(0);
        PasswordBatchDeleteGlobalProgressState state = tracker.getProgress().getValue();
        assertNotNull("Zero successful deletes must not erase the failure", state);
        assertEquals(4, state.getTotal());
        assertEquals(Integer.valueOf(0), state.getSuccessCount());
        assertNotEquals(PasswordBatchDeletePhase.SUCCESS, state.getPhase());
    }

    @Test
    public void completeDeletionReportsTheConfirmedCount() {
        tracker.update(0, 5);
        tracker.update(5, 5);
        tracker.complete(5);
        PasswordBatchDeleteGlobalProgressState state = tracker.getProgress().getValue();
        assertNotNull(state);
        assertEquals(5, state.getTotal());
        assertEquals(Integer.valueOf(5), state.getSuccessCount());
        assertEquals(PasswordBatchDeletePhase.SUCCESS, state.getPhase());
    }

    @Test
    public void interruptedDeletionDoesNotClaimProcessedItemsWereDeleted() {
        tracker.update(8, 10);
        tracker.fail();
        PasswordBatchDeleteGlobalProgressState state = tracker.getProgress().getValue();
        assertNotNull(state);
        assertEquals(10, state.getTotal());
        assertEquals(8, state.getProcessed());
        assertNull(state.getSuccessCount());
        assertEquals(PasswordBatchDeletePhase.FAILURE, state.getPhase());
    }
}
