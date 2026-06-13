package takagi.ru.monica.notes.domain

import android.content.Context

interface NoteDraftStorage {
    fun saveDraft(noteId: Long, title: String, content: String, tagsText: String)
    fun loadDraft(noteId: Long): NoteDraftStore.NoteDraft?
    fun clearDraft(noteId: Long)
    fun hasDraft(noteId: Long): Boolean
}

class NoteDraftStore(context: Context) : NoteDraftStorage {

    companion object {
        @Volatile
        private var instance: NoteDraftStore? = null

        fun init(context: Context): NoteDraftStore {
            return instance ?: synchronized(this) {
                instance ?: NoteDraftStore(context.applicationContext).also { instance = it }
            }
        }

        fun get(): NoteDraftStore = instance
            ?: throw IllegalStateException("NoteDraftStore not initialized. Call init(context) first.")
    }

    data class NoteDraft(
        val title: String,
        val content: String,
        val tagsText: String
    )

    private val prefs = context.applicationContext
        .getSharedPreferences("note_drafts", Context.MODE_PRIVATE)

    override fun saveDraft(noteId: Long, title: String, content: String, tagsText: String) {
        if (title.isBlank() && content.isBlank() && tagsText.isBlank()) {
            clearDraft(noteId)
            return
        }
        prefs.edit()
            .putString(key(noteId, "title"), title)
            .putString(key(noteId, "content"), content)
            .putString(key(noteId, "tags"), tagsText)
            .putLong(key(noteId, "ts"), System.currentTimeMillis())
            .apply()
    }

    override fun loadDraft(noteId: Long): NoteDraft? {
        if (!prefs.contains(key(noteId, "content"))) return null
        val content = prefs.getString(key(noteId, "content"), null) ?: return null
        val title = prefs.getString(key(noteId, "title"), "") ?: ""
        val tags = prefs.getString(key(noteId, "tags"), "") ?: ""
        return NoteDraft(title = title, content = content, tagsText = tags)
    }

    override fun clearDraft(noteId: Long) {
        prefs.edit()
            .remove(key(noteId, "title"))
            .remove(key(noteId, "content"))
            .remove(key(noteId, "tags"))
            .remove(key(noteId, "ts"))
            .apply()
    }

    override fun hasDraft(noteId: Long): Boolean {
        return prefs.contains(key(noteId, "content"))
    }

    private fun key(noteId: Long, field: String): String = "$noteId.$field"
}
