package takagi.ru.monica.passkey

import android.content.Context
import org.json.JSONObject

data class PasskeyValidationBucket(
    val packageName: String,
    val rpId: String,
    val total: Int,
    val suspicious: Int,
    val strictCandidates: Int,
    val lastAt: Long,
    val lastReasons: String
)

data class PasskeyValidationSummary(
    val total: Int,
    val suspicious: Int,
    val strictCandidates: Int,
    val lastAt: Long,
    val buckets: List<PasskeyValidationBucket>
)

object PasskeyValidationDiagnostics {

    private const val PREF_NAME = "passkey_validation_diagnostics"
    private const val KEY_STATE_JSON = "state_json"
    private const val KEY_VERSION = "version"
    private const val VERSION = 1

    private const val F_TOTAL = "total"
    private const val F_SUSPICIOUS = "suspicious"
    private const val F_STRICT = "strict"
    private const val F_LAST_AT = "lastAt"
    private const val F_BUCKETS = "buckets"
    private const val F_LAST_REASONS = "lastReasons"

    @Synchronized
    fun record(
        context: Context,
        flowTag: String,
        rpId: String?,
        callingPackage: String?,
        verdict: PasskeyValidationVerdict
    ) {
        val prefs = prefs(context)
        val root = readRootJson(prefs)
        val buckets = root.optJSONObject(F_BUCKETS) ?: JSONObject().also { root.put(F_BUCKETS, it) }

        root.put(F_TOTAL, root.optInt(F_TOTAL) + 1)
        if (verdict.reasons.isNotEmpty()) {
            root.put(F_SUSPICIOUS, root.optInt(F_SUSPICIOUS) + 1)
        }
        if (verdict.strictBlock) {
            root.put(F_STRICT, root.optInt(F_STRICT) + 1)
        }
        root.put(F_LAST_AT, System.currentTimeMillis())

        val pkg = callingPackage?.takeIf { it.isNotBlank() } ?: "unknown_pkg"
        val rp = PasskeyRpIdNormalizer.normalize(rpId)?.takeIf { it.isNotBlank() } ?: "unknown_rp"
        val bucketKey = "$pkg::$rp"
        val bucket = buckets.optJSONObject(bucketKey) ?: JSONObject().also { buckets.put(bucketKey, it) }

        bucket.put(F_TOTAL, bucket.optInt(F_TOTAL) + 1)
        if (verdict.reasons.isNotEmpty()) {
            bucket.put(F_SUSPICIOUS, bucket.optInt(F_SUSPICIOUS) + 1)
        }
        if (verdict.strictBlock) {
            bucket.put(F_STRICT, bucket.optInt(F_STRICT) + 1)
        }
        bucket.put(F_LAST_AT, System.currentTimeMillis())
        bucket.put(F_LAST_REASONS, verdict.reasons.joinToString(","))
        bucket.put("flowTag", flowTag)

        prefs.edit()
            .putInt(KEY_VERSION, VERSION)
            .putString(KEY_STATE_JSON, root.toString())
            .apply()
    }

    @Synchronized
    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_STATE_JSON).remove(KEY_VERSION).apply()
    }

    fun readSummary(context: Context): PasskeyValidationSummary {
        val root = readRootJson(prefs(context))
        val bucketsObj = root.optJSONObject(F_BUCKETS) ?: JSONObject()
        val buckets = mutableListOf<PasskeyValidationBucket>()
        val keys = bucketsObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val bucketObj = bucketsObj.optJSONObject(key) ?: continue
            val split = key.split("::", limit = 2)
            val pkg = split.getOrNull(0) ?: "unknown_pkg"
            val rp = split.getOrNull(1) ?: "unknown_rp"
            buckets += PasskeyValidationBucket(
                packageName = pkg,
                rpId = rp,
                total = bucketObj.optInt(F_TOTAL),
                suspicious = bucketObj.optInt(F_SUSPICIOUS),
                strictCandidates = bucketObj.optInt(F_STRICT),
                lastAt = bucketObj.optLong(F_LAST_AT),
                lastReasons = bucketObj.optString(F_LAST_REASONS)
            )
        }
        val sortedBuckets = buckets.sortedWith(
            compareByDescending<PasskeyValidationBucket> { it.suspicious }
                .thenByDescending { it.total }
                .thenByDescending { it.lastAt }
        )
        return PasskeyValidationSummary(
            total = root.optInt(F_TOTAL),
            suspicious = root.optInt(F_SUSPICIOUS),
            strictCandidates = root.optInt(F_STRICT),
            lastAt = root.optLong(F_LAST_AT),
            buckets = sortedBuckets
        )
    }

    fun buildReport(context: Context): String {
        val summary = readSummary(context)
        val lines = mutableListOf<String>()
        lines += "Passkey Validation Diagnostics"
        lines += "total=${summary.total}, suspicious=${summary.suspicious}, strictCandidates=${summary.strictCandidates}, lastAt=${summary.lastAt}"
        if (summary.buckets.isEmpty()) {
            lines += "No bucket records."
        } else {
            lines += "Top Buckets:"
            summary.buckets.take(20).forEachIndexed { idx, bucket ->
                lines += "${idx + 1}. pkg=${bucket.packageName}, rp=${bucket.rpId}, total=${bucket.total}, suspicious=${bucket.suspicious}, strict=${bucket.strictCandidates}, reasons=${bucket.lastReasons}"
            }
        }
        return lines.joinToString("\n")
    }

    private fun readRootJson(prefs: android.content.SharedPreferences): JSONObject {
        val raw = prefs.getString(KEY_STATE_JSON, null)
        val parsed = runCatching { if (raw.isNullOrBlank()) JSONObject() else JSONObject(raw) }.getOrDefault(JSONObject())
        if (!parsed.has(F_BUCKETS)) parsed.put(F_BUCKETS, JSONObject())
        return parsed
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}

