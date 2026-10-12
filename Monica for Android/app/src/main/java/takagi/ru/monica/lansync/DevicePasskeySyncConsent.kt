package takagi.ru.monica.lansync

import takagi.ru.monica.passkey.PasskeyPortability

/** Display metadata only. Derive from the validated export, never from the badge cache. */
internal data class DevicePasskeySyncIssue(
    val identity: String,
    val account: String,
    val relyingParty: String,
    val portability: PasskeyPortability,
    val skipForCounter: Boolean = false
) {
    companion object {
        fun from(snapshot: DevicePasswordSnapshot, signCount: Long? = null): DevicePasskeySyncIssue? {
            if (snapshot.deleted || snapshot.data.optString("entry_type") != "passkey") return null
            val payload = snapshot.data.getJSONObject("payload")
            val status = PasskeyPortability.classify(
                payload.opt("backup_eligible") as? Boolean, payload.opt("backup_state") as? Boolean,
                signCount ?: payload.optLong("sign_count"), payload.getInt("public_key_algorithm"),
                usable = true, exportable = payload.getString("key_status") == "portable")
            if (status == PasskeyPortability.PORTABLE) return null
            return DevicePasskeySyncIssue(
                snapshot.localId + ":" + payload.getString("credential_id"),
                payload.optString("user_display_name").ifBlank { payload.optString("user_name") }
                    .ifBlank { snapshot.data.optString("title") }, payload.getString("rp_id"), status,
                (signCount ?: payload.optLong("sign_count")) > 0)
        }
    }
}

internal data class DevicePasskeySyncReview(val issues: List<DevicePasskeySyncIssue>, val peers: Set<String>)

/** Owned by the coordinator mutex. Acknowledgements last only for this app process. */
internal class DevicePasskeySyncConsent {
    private val accepted = mutableMapOf<String, MutableSet<Triple<String, PasskeyPortability, Boolean>>>()
    var pending: DevicePasskeySyncReview? = null
        private set

    fun review(issues: List<DevicePasskeySyncIssue>, peers: Set<String>, suppressNotice: Boolean = false): DevicePasskeySyncReview? {
        if (suppressNotice) { pending = null; return null }
        val unseen = issues.filter { issue ->
            peers.any { peer -> Triple(issue.identity, issue.portability, issue.skipForCounter) !in accepted[peer].orEmpty() }
        }
        pending = if (unseen.isEmpty()) null else DevicePasskeySyncReview(unseen, peers.toSet())
        return pending
    }

    fun confirm(review: DevicePasskeySyncReview): Boolean {
        if (pending != review) return false
        review.peers.forEach { peer ->
            accepted.getOrPut(peer) { mutableSetOf() }.addAll(review.issues.map { Triple(it.identity, it.portability, it.skipForCounter) })
        }
        pending = null
        return true
    }

    fun reset() { accepted.clear(); clearPending() }
    fun clearPending() { pending = null }
    fun forget(peer: String) { accepted.remove(peer); clearPending() }
}
