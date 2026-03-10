package takagi.ru.monica.viewmodel

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.notes.domain.NoteContentCodec
import takagi.ru.monica.utils.RememberedStorageTarget
import java.util.Date

data class NoteEditorUiState(
    val title: String = "",
    val contentField: TextFieldValue = TextFieldValue(""),
    val isMarkdownPreview: Boolean = false,
    val tagsText: String = "",
    val isFavorite: Boolean = false,
    val selectedCategoryId: Long? = null,
    val keepassDatabaseId: Long? = null,
    val keepassGroupPath: String? = null,
    val bitwardenVaultId: Long? = null,
    val bitwardenFolderId: String? = null,
    val hasAppliedInitialStorage: Boolean = false,
    val isSaving: Boolean = false,
    val createdAt: Date = Date(),
    val currentNote: SecureItem? = null,
    val noteImagePaths: List<String> = emptyList(),
    val deletedImagePaths: List<String> = emptyList()
)

data class NoteSavePayload(
    val title: String,
    val content: String,
    val imagePathsJson: String,
    val isMarkdown: Boolean,
    val tags: List<String>
)

private data class NoteEditDraft(
    val title: String,
    val content: String,
    val isMarkdown: Boolean,
    val tags: List<String>,
    val isFavorite: Boolean,
    val categoryId: Long?,
    val keepassDatabaseId: Long?,
    val keepassGroupPath: String?,
    val bitwardenVaultId: Long?,
    val bitwardenFolderId: String?,
    val imagePaths: List<String>,
    val createdAt: Date
)

class NoteEditorViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(NoteEditorUiState())
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()

    fun resetForNewNote() {
        _uiState.value = NoteEditorUiState()
    }

    fun loadForEdit(note: SecureItem) {
        val draft = note.toNoteEditDraft()
        val hydratedContent = NoteContentCodec.appendInlineImageRefs(
            content = draft.content.trimEnd(),
            imageIds = draft.imagePaths
        )
        val hydratedField = TextFieldValue(hydratedContent)
        val inlineImagePaths = NoteContentCodec.extractInlineImageIds(hydratedField.text)
        _uiState.update {
            it.copy(
                title = draft.title,
                contentField = hydratedField,
                isMarkdownPreview = false,
                tagsText = draft.tags.joinToString(", "),
                isFavorite = draft.isFavorite,
                selectedCategoryId = draft.categoryId,
                keepassDatabaseId = draft.keepassDatabaseId,
                keepassGroupPath = draft.keepassGroupPath,
                bitwardenVaultId = draft.bitwardenVaultId,
                bitwardenFolderId = draft.bitwardenFolderId,
                createdAt = draft.createdAt,
                currentNote = note,
                noteImagePaths = inlineImagePaths,
                deletedImagePaths = emptyList()
            )
        }
    }

    fun applyInitialStorageIfNeeded(
        isEditing: Boolean,
        initialCategoryId: Long?,
        initialKeePassDatabaseId: Long?,
        initialBitwardenVaultId: Long?,
        initialBitwardenFolderId: String?,
        draftStorageTarget: NoteDraftStorageTarget,
        rememberedStorageTarget: RememberedStorageTarget?
    ) {
        val current = _uiState.value
        if (isEditing || current.hasAppliedInitialStorage) return
        val remembered = rememberedStorageTarget ?: return

        _uiState.update {
            it.copy(
                selectedCategoryId = initialCategoryId ?: draftStorageTarget.categoryId ?: remembered.categoryId,
                keepassDatabaseId = initialKeePassDatabaseId ?: draftStorageTarget.keepassDatabaseId ?: remembered.keepassDatabaseId,
                keepassGroupPath = draftStorageTarget.keepassGroupPath ?: remembered.keepassGroupPath,
                bitwardenVaultId = initialBitwardenVaultId ?: draftStorageTarget.bitwardenVaultId ?: remembered.bitwardenVaultId,
                bitwardenFolderId = initialBitwardenFolderId ?: draftStorageTarget.bitwardenFolderId ?: remembered.bitwardenFolderId,
                hasAppliedInitialStorage = true
            )
        }
    }

    fun updateTitle(title: String) {
        _uiState.update { it.copy(title = title) }
    }

    fun updateContent(content: TextFieldValue) {
        applyContentField(content)
    }

    fun updatePreviewMode(isPreview: Boolean) {
        _uiState.update { it.copy(isMarkdownPreview = isPreview) }
    }

    fun updateTagsText(tags: String) {
        _uiState.update { it.copy(tagsText = tags) }
    }

    fun toggleFavorite() {
        _uiState.update { it.copy(isFavorite = !it.isFavorite) }
    }

    fun selectCategory(categoryId: Long?) {
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
    }

    fun selectKeePassDatabase(databaseId: Long?) {
        _uiState.update {
            it.copy(
                keepassDatabaseId = databaseId,
                keepassGroupPath = if (databaseId == it.keepassDatabaseId) it.keepassGroupPath else null,
                bitwardenVaultId = if (databaseId != null) null else it.bitwardenVaultId,
                bitwardenFolderId = if (databaseId != null) null else it.bitwardenFolderId
            )
        }
    }

    fun selectBitwardenVault(vaultId: Long?) {
        _uiState.update {
            it.copy(
                bitwardenVaultId = vaultId,
                keepassGroupPath = if (vaultId != null) null else it.keepassGroupPath,
                keepassDatabaseId = if (vaultId != null) null else it.keepassDatabaseId
            )
        }
    }

    fun selectBitwardenFolder(folderId: String?) {
        _uiState.update {
            it.copy(
                bitwardenFolderId = folderId,
                keepassGroupPath = if (it.bitwardenVaultId != null) null else it.keepassGroupPath,
                keepassDatabaseId = if (it.bitwardenVaultId != null) null else it.keepassDatabaseId
            )
        }
    }

    fun insertInlineImage(imageId: String, insertionIndex: Int?) {
        val current = _uiState.value
        val updatedContent = insertInlineImageAtSelection(
            current = current.contentField,
            imageId = imageId,
            insertionIndex = insertionIndex
        )
        applyContentField(
            content = updatedContent,
            forcePreviewMode = false,
            clearDeletedIds = setOf(imageId)
        )
    }

    fun removeInlineImage(imageId: String) {
        val current = _uiState.value
        val updated = NoteContentCodec.removeInlineImageRef(current.contentField.text, imageId)
        applyContentField(
            content = current.contentField.copy(
                text = updated,
                selection = TextRange(updated.length)
            )
        )
    }

    fun canSave(): Boolean {
        val state = _uiState.value
        return state.title.isNotBlank() ||
            state.contentField.text.isNotBlank() ||
            state.noteImagePaths.isNotEmpty()
    }

    fun tryStartSaving(): Boolean {
        val current = _uiState.value
        if (current.isSaving || !canSave()) return false
        _uiState.update { it.copy(isSaving = true) }
        return true
    }

    fun stopSaving() {
        _uiState.update { it.copy(isSaving = false) }
    }

    fun buildSavePayload(isMarkdown: Boolean): NoteSavePayload {
        val state = _uiState.value
        val tags = state.tagsText
            .split(',', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val normalizedContent = state.contentField.text.trimEnd()
        val finalTitle = if (state.title.isNotBlank()) {
            state.title.trim()
        } else {
            normalizedContent.lines().firstOrNull()?.take(100)?.trim() ?: ""
        }
        val imagePaths = NoteContentCodec.extractInlineImageIds(normalizedContent)
        return NoteSavePayload(
            title = finalTitle,
            content = normalizedContent,
            imagePathsJson = NoteContentCodec.encodeImagePaths(imagePaths),
            isMarkdown = isMarkdown,
            tags = tags
        )
    }

    private fun applyContentField(
        content: TextFieldValue,
        forcePreviewMode: Boolean? = null,
        clearDeletedIds: Set<String> = emptySet()
    ) {
        val current = _uiState.value
        val inlineImagePaths = NoteContentCodec.extractInlineImageIds(content.text)
        val removed = current.noteImagePaths.filterNot { inlineImagePaths.contains(it) }
        val deleted = (current.deletedImagePaths + removed)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .filterNot { inlineImagePaths.contains(it) || clearDeletedIds.contains(it) }

        _uiState.update {
            it.copy(
                contentField = content,
                isMarkdownPreview = forcePreviewMode ?: it.isMarkdownPreview,
                noteImagePaths = inlineImagePaths,
                deletedImagePaths = deleted
            )
        }
    }
}

private fun SecureItem.toNoteEditDraft(): NoteEditDraft {
    val decoded = NoteContentCodec.decodeFromItem(this)
    return NoteEditDraft(
        title = title,
        content = decoded.content,
        isMarkdown = decoded.isMarkdown,
        tags = decoded.tags,
        isFavorite = isFavorite,
        categoryId = categoryId,
        keepassDatabaseId = keepassDatabaseId,
        keepassGroupPath = keepassGroupPath,
        bitwardenVaultId = bitwardenVaultId,
        bitwardenFolderId = bitwardenFolderId,
        imagePaths = NoteContentCodec.decodeImagePaths(imagePaths),
        createdAt = createdAt
    )
}

private fun insertInlineImageAtSelection(
    current: TextFieldValue,
    imageId: String,
    insertionIndex: Int? = null
): TextFieldValue {
    val markdownRef = NoteContentCodec.buildInlineImageMarkdown(imageId)
    if (markdownRef.isBlank()) return current

    val explicitIndex = insertionIndex?.coerceIn(0, current.text.length)
    val selectionStart = explicitIndex ?: current.selection.start.coerceIn(0, current.text.length)
    val selectionEnd = explicitIndex ?: current.selection.end.coerceIn(0, current.text.length)
    val prefix = current.text.substring(0, selectionStart)
    val suffix = current.text.substring(selectionEnd)

    val separatorBefore = if (prefix.isNotBlank() && !prefix.endsWith("\n")) "\n\n" else ""
    val separatorAfter = if (suffix.isNotBlank() && !suffix.startsWith("\n")) "\n\n" else ""
    val insertion = "$separatorBefore$markdownRef$separatorAfter"
    val updatedText = prefix + insertion + suffix
    val cursor = (prefix.length + insertion.length).coerceAtMost(updatedText.length)
    return current.copy(text = updatedText, selection = TextRange(cursor))
}
