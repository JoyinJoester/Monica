package takagi.ru.monica.autofill_ng.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutofillProtectionStateTest {
    private val eligible = AutofillProtectionState(
        backgroundEnabled = true,
        enhancedRecoveryEnabled = true,
        runtime = ProtectionRuntime.RUNNING,
        accessibility = AccessibilityConnection.DISCONNECTED,
        shizuku = ShizukuAccess.ADB_AUTHORIZED,
    )

    @Test fun enabledPermissionIsNotMistakenForAWorkingConnection() {
        assertEquals(AccessibilityConnection.DISCONNECTED, AutofillProtectionState.accessibilityConnection(true, false))
        assertEquals(AccessibilityConnection.CONNECTED, AutofillProtectionState.accessibilityConnection(true, true))
    }

    @Test fun disabledPermissionOverridesAStaleConnectedInstance() {
        assertEquals(AccessibilityConnection.DISABLED, AutofillProtectionState.accessibilityConnection(false, true))
        assertFalse(eligible.copy(accessibility = AccessibilityConnection.DISABLED).canRecoverAccessibility())
    }

    @Test fun healthyServicesAreNeverRestarted() {
        assertFalse(eligible.copy(accessibility = AccessibilityConnection.CONNECTED).canRecoverAccessibility())
    }

    @Test fun bothProtectionAndEnhancedConsentAreRequired() {
        assertFalse(eligible.copy(backgroundEnabled = false).canRecoverAccessibility())
        assertFalse(eligible.copy(enhancedRecoveryEnabled = false).canRecoverAccessibility())
    }

    @Test fun rootMissingRevokedAndUnsupportedBackendsCannotRecover() {
        for (access in ShizukuAccess.entries.filterNot { it == ShizukuAccess.ADB_AUTHORIZED }) {
            assertFalse("Unexpected eligibility for $access", eligible.copy(shizuku = access).canRecoverAccessibility())
        }
    }

    @Test fun firstAuthorizedAttemptIsAllowedOnlyWhileProtectionIsRunning() {
        assertTrue(eligible.shouldRecoverAutomatically(1_000, 0))
        assertFalse(eligible.copy(runtime = ProtectionRuntime.STOPPED).shouldRecoverAutomatically(1_000, 0))
        assertFalse(eligible.copy(runtime = ProtectionRuntime.STARTING).shouldRecoverAutomatically(1_000, 0))
    }

    @Test fun persistentCooldownPreventsRecoveryLoopsAcrossProcessRestarts() {
        val previous = 1_000_000L
        assertFalse(eligible.shouldRecoverAutomatically(previous + 1, previous))
        assertFalse(eligible.shouldRecoverAutomatically(previous + AutofillProtectionState.RECOVERY_COOLDOWN_MS - 1, previous))
        assertTrue(eligible.shouldRecoverAutomatically(previous + AutofillProtectionState.RECOVERY_COOLDOWN_MS, previous))
    }

    @Test fun movingClockBackDoesNotBypassTheCooldown() {
        assertFalse(eligible.shouldRecoverAutomatically(1_000, 2_000))
    }

    @Test fun directRootWorksWithoutShizukuOnlyWhenSelectedAndAuthorized() {
        val root = eligible.copy(useRoot = true, rootAuthorized = true, shizuku = ShizukuAccess.UNAVAILABLE)
        assertTrue(root.canRecoverAccessibility())
        assertFalse(root.copy(rootAuthorized = false, shizuku = ShizukuAccess.ADB_AUTHORIZED).canRecoverAccessibility())
        assertFalse(root.copy(useRoot = false).canRecoverAccessibility())
        assertFalse(root.copy(backgroundEnabled = false).canRecoverAccessibility())
        assertFalse(root.copy(enhancedRecoveryEnabled = false).canRecoverAccessibility())
        assertFalse(root.copy(accessibility = AccessibilityConnection.DISABLED).canRecoverAccessibility())
        assertFalse(root.copy(accessibility = AccessibilityConnection.CONNECTED).canRecoverAccessibility())
    }
}
