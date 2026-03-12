package takagi.ru.monica.data.dedup

import android.content.Context

class DedupIgnoreStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun isIgnored(clusterId: String): Boolean {
        return prefs.getStringSet(KEY_IGNORED_CLUSTER_IDS, emptySet()).orEmpty().contains(clusterId)
    }

    fun ignore(clusterId: String) {
        val current = prefs.getStringSet(KEY_IGNORED_CLUSTER_IDS, emptySet()).orEmpty().toMutableSet()
        current += clusterId
        prefs.edit().putStringSet(KEY_IGNORED_CLUSTER_IDS, current).apply()
    }

    companion object {
        private const val PREFS_NAME = "dedup_engine_prefs"
        private const val KEY_IGNORED_CLUSTER_IDS = "ignored_cluster_ids"
    }
}
