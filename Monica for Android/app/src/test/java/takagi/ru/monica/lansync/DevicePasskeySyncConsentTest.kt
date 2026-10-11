package takagi.ru.monica.lansync

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import takagi.ru.monica.passkey.PasskeyPortability

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class DevicePasskeySyncConsentTest {
    private fun record(scope: String = "passkeys", reference: Boolean = false, backup: Boolean = true,
        count: Long = 0, algorithm: Int = -7): DevicePasswordSnapshot {
        val payload = JSONObject().put("credential_id", "fixture").put("rp_id", "example.test")
            .put("user_name", "Fixture").put("key_status", if (reference) "reference" else "portable")
            .put("backup_eligible", backup).put("backup_state", false).put("sign_count", count)
            .put("public_key_algorithm", algorithm)
        return DevicePasswordSnapshot("$scope:1", "", scope, "", false,
            JSONObject().put("entry_type", "passkey").put("payload", payload))
    }

    @Test fun exports_classify_both_room_and_native_limits_without_private_keys_in_the_review() {
        assertNull(DevicePasskeySyncIssue.from(record()))
        assertEquals(PasskeyPortability.DEVICE_ONLY, DevicePasskeySyncIssue.from(record(reference = true))!!.portability)
        assertEquals(PasskeyPortability.BACKUP_RESTRICTED, DevicePasskeySyncIssue.from(record("mdbx:1", backup = false))!!.portability)
        assertEquals(PasskeyPortability.ALGORITHM_RESTRICTED, DevicePasskeySyncIssue.from(record(algorithm = -257))!!.portability)
        assertEquals(PasskeyPortability.COUNTER_HISTORY, DevicePasskeySyncIssue.from(record(), signCount = 42)!!.portability)
        assertNull(DevicePasskeySyncIssue.from(record(reference = true).copy(deleted = true)))
    }

    @Test fun manual_and_automatic_attempts_stay_blocked_until_confirmed() {
        val consent = DevicePasskeySyncConsent()
        val issues = listOf(DevicePasskeySyncIssue.from(record(reference = true))!!)
        val first = consent.review(issues, setOf("phone"))!!
        repeat(3) { assertEquals(first, consent.review(issues, setOf("phone"))) }
        consent.clearPending() // Cancellation/locking never counts as acceptance.
        consent.confirm(first)
        assertNotNull(consent.review(issues, setOf("phone")))
        consent.confirm(consent.pending!!)
        assertNull(consent.review(issues, setOf("phone")))
    }

    @Test fun new_credentials_restrictions_and_peers_require_another_review() {
        val consent = DevicePasskeySyncConsent()
        val issue = DevicePasskeySyncIssue.from(record(reference = true))!!
        consent.confirm(consent.review(listOf(issue), setOf("phone"))!!)
        assertNotNull(consent.review(listOf(issue), setOf("phone", "desktop")))
        consent.confirm(consent.pending!!)
        assertNull(consent.review(listOf(issue), setOf("phone", "desktop")))
        val fresh = issue.copy(identity = "another-credential")
        assertEquals(listOf(fresh), consent.review(listOf(issue, fresh), setOf("phone"))!!.issues)
        val changed = issue.copy(portability = PasskeyPortability.BACKUP_RESTRICTED)
        assertEquals(listOf(changed), consent.review(listOf(changed), setOf("phone"))!!.issues)
        consent.forget("phone")
        assertNotNull(consent.review(listOf(issue), setOf("phone")))
    }

    @Test fun stale_dialog_cannot_approve_new_content_and_receive_only_has_no_local_review() {
        val consent = DevicePasskeySyncConsent()
        val issue = DevicePasskeySyncIssue.from(record(reference = true))!!
        val old = consent.review(listOf(issue), setOf("phone"))!!
        val latest = consent.review(listOf(issue.copy(identity = "new")), setOf("phone"))!!
        consent.confirm(old)
        assertEquals(latest, consent.pending)
        assertNull(consent.review(emptyList(), setOf("phone")))
        assertNull(consent.pending)
        assertNotNull(consent.review(listOf(issue), setOf("phone")))
    }
}
