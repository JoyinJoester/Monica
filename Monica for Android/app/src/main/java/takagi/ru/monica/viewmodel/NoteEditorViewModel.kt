package takagi.ru.monica.viewmodel

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.model.StorageTarget
import takagi.ru.monica.data.model.normalizedStorageTargets
import takagi.ru.monica.data.model.toStorageTarget
import takagi.ru.monica.data.model.withStorageTargetSelected
import takagi.ru.monica.data.model.withoutStorageTarget
import takagi.ru.monica.notes.domain.NoteContentCodec
import takagi.ru.monica.notes.domain.NoteDraftStore
import takagi.ru.monica.notes.domain.NoteDraftStorage
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
    val mdbxDatabaseId: Long? = null,
    val bitwardenVaultId: Long? = null,
    val bitwardenFolderId: String? = null,
    val selectedStorageTargets: List<StorageTarget> = emptyList(),
    val existingReplicaTargetKeys: Set<String> = emptySet(),
    val currentReplicaGroupId: String? = null,
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
    val mdbxDatabaseId: Long?,
    val bitwardenVaultId: Long?,
    val bitwardenFolderId: String?,
    val imagePaths: List<String>,
    val createdAt: Date
)

class NoteEditorViewModel(
    private val draftStorageProvider: () -> NoteDraftStorage = { NoteDraftStore.get() }
) : ViewModel() {
    companion object {
        private const val NEW_NOTE_ID = -1L
        private const val AUTO_SAVE_DEBOUNCE_MS = 2000L
    }

    private val draftStore by lazy { draftStorageProvider() }
    private val _uiState = MutableStateFlow(NoteEditorUiState())
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()
    private var draftSaveJob: Job? = null
    private var currentNoteId: Long = NEW_NOTE_ID

    fun resetForNewNote() {
        draftSaveJob?.cancel()
        currentNoteId = NEW_NOTE_ID
        _uiState.value = NoteEditorUiState()
    }

    fun loadForEdit(note: SecureItem) {
        draftSaveJob?.cancel()
        currentNoteId = note.id
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
                mdbxDatabaseId = draft.mdbxDatabaseId,
                bitwardenVaultId = draft.bitwardenVaultId,
                bitwardenFolderId = draft.bitwardenFolderId,
                selectedStorageTargets = listOf(note.toStorageTarget()),
                existingReplicaTargetKeys = emptySet(),
                currentReplicaGroupId = note.replicaGroupId,
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
        initialKeePassGroupPath: String?,
        initialMdbxDatabaseId: Long?,
        initialBitwardenVaultId: Long?,
        initialBitwardenFolderId: String?,
        draftStorageTarget: NoteDraftStorageTarget,
        rememberedStorageTarget: RememberedStorageTarget?
    ) {
        val current = _uiState.value
        if (isEditing || current.hasAppliedInitialStorage) return
        val normalizedInitialKeePassGroupPath = initialKeePassGroupPath?.takeIf { it.isNotBlank() }
        val normalizedInitialBitwardenFolderId = initialBitwardenFolderId?.takeIf { it.isNotBlank() }
        val normalizedDraftKeePassGroupPath = draftStorageTarget.keepassGroupPath?.takeIf { it.isNotBlank() }
        val normalizedDraftBitwardenFolderId = draftStorageTarget.bitwardenFolderId?.takeIf { it.isNotBlank() }
        val normalizedRememberedKeePassGroupPath = rememberedStorageTarget?.keepassGroupPath?.takeIf { it.isNotBlank() }
        val normalizedRememberedBitwardenFolderId = rememberedStorageTarget?.bitwardenFolderId?.takeIf { it.isNotBlank() }

        val resolvedCategoryId =
            initialCategoryId ?: draftStorageTarget.categoryId ?: rememberedStorageTarget?.categoryId
        val resolvedKeepassDatabaseId =
            initialKeePassDatabaseId ?: draftStorageTarget.keepassDatabaseId ?: rememberedStorageTarget?.keepassDatabaseId
        val resolvedKeepassGroupPath =
            normalizedInitialKeePassGroupPath ?: normalizedDraftKeePassGroupPath ?: normalizedRememberedKeePassGroupPath
        val resolvedMdbxDatabaseId =
            initialMdbxDatabaseId ?: draftStorageTarget.mdbxDatabaseId ?: rememberedStorageTarget?.mdbxDatabaseId
        val resolvedBitwardenVaultId =
            initialBitwardenVaultId ?: draftStorageTarget.bitwardenVaultId ?: rememberedStorageTarget?.bitwardenVaultId
        val resolvedBitwardenFolderId =
            normalizedInitialBitwardenFolderId ?: normalizedDraftBitwardenFolderId ?: normalizedRememberedBitwardenFolderId

        val hasResolvedStorage = resolvedCategoryId != null ||
            resolvedKeepassDatabaseId != null ||
            resolvedKeepassGroupPath != null ||
            resolvedMdbxDatabaseId != null ||
            resolvedBitwardenVaultId != null ||
            resolvedBitwardenFolderId != null
        if (!hasResolvedStorage) {
            val defaultTarget = StorageTarget.MonicaLocal(null)
            _uiState.update {
                it.copy(
                    selectedCategoryId = null,
                    keepassDatabaseId = null,
                    keepassGroupPath = null,
                    mdbxDatabaseId = null,
                    bitwardenVaultId = null,
                    bitwardenFolderId = null,
                    selectedStorageTargets = listOf(defaultTarget),
                    existingReplicaTargetKeys = emptySet(),
                    currentReplicaGroupId = null,
                    hasAppliedInitialStorage = true
                )
            }
            return
        }

        val initialTarget = when {
            resolvedBitwardenVaultId != null -> StorageTarget.Bitwarden(
                vaultId = resolvedBitwardenVaultId,
                folderId = resolvedBitwardenFolderId
            )
            resolvedKeepassDatabaseId != null -> StorageTarget.KeePass(
                databaseId = resolvedKeepassDatabaseId,
                groupPath = resolvedKeepassGroupPath
            )
            resolvedMdbxDatabaseId != null -> StorageTarget.Mdbx(resolvedMdbxDatabaseId)
            else -> StorageTarget.MonicaLocal(resolvedCategoryId)
        }

        _uiState.update {
            it.copy(
                selectedCategoryId = resolvedCategoryId,
                keepassDatabaseId = resolvedKeepassDatabaseId,
                keepassGroupPath = resolvedKeepassGroupPath,
                mdbxDatabaseId = resolvedMdbxDatabaseId,
                bitwardenVaultId = resolvedBitwardenVaultId,
                bitwardenFolderId = resolvedBitwardenFolderId,
                selectedStorageTargets = listOf(initialTarget),
                existingReplicaTargetKeys = emptySet(),
                currentReplicaGroupId = null,
                hasAppliedInitialStorage = true
            )
        }
    }

    fun updateTitle(title: String) {
        _uiState.update { it.copy(title = title) }
        scheduleDraftSave()
    }

    fun updateContent(content: TextFieldValue) {
        applyContentField(content)
        scheduleDraftSave()
    }

    fun updatePreviewMode(isPreview: Boolean) {
        _uiState.update { it.copy(isMarkdownPreview = isPreview) }
    }

    fun updateTagsText(tags: String) {
        _uiState.update { it.copy(tagsText = tags) }
        scheduleDraftSave()
    }

    fun toggleFavorite() {
        _uiState.update { it.copy(isFavorite = !it.isFavorite) }
    }

    fun selectCategory(categoryId: Long?) {
        _uiState.update {
            it.copy(
                selectedCategoryId = categoryId,
                mdbxDatabaseId = null
            )
        }
    }

    fun selectKeePassDatabase(databaseId: Long?) {
        _uiState.update {
            it.copy(
                keepassDatabaseId = databaseId,
                keepassGroupPath = if (databaseId == it.keepassDatabaseId) it.keepassGroupPath else null,
                mdbxDatabaseId = null,
                bitwardenVaultId = if (databaseId != null) null else it.bitwardenVaultId,
                bitwardenFolderId = if (databaseId != null) null else it.bitwardenFolderId
            )
        }
    }

    fun selectMdbxDatabase(databaseId: Long?) {
        _uiState.update {
            it.copy(
                selectedCategoryId = null,
                keepassDatabaseId = null,
                keepassGroupPath = null,
                mdbxDatabaseId = databaseId,
                bitwardenVaultId = null,
                bitwardenFolderId = null
            )
        }
    }

    fun selectBitwardenVault(vaultId: Long?) {
        _uiState.update {
            it.copy(
                bitwardenVaultId = vaultId,
                mdbxDatabaseId = null,
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

    fun setSelectedStorageTargets(
        targets: List<StorageTarget>,
        existingTargetKeys: Set<String> = _uiState.value.existingReplicaTargetKeys,
        replicaGroupId: String? = _uiState.value.currentReplicaGroupId
    ) {
        val normalizedTargets = targets.normalizedStorageTargets()
        val primaryTarget = normalizedTargets.first()
        _uiState.update {
            it.copy(
                selectedCategoryId = (primaryTarget as? StorageTarget.MonicaLocal)?.categoryId,
                keepassDatabaseId = (primaryTarget as? StorageTarget.KeePass)?.databaseId,
                keepassGroupPath = (primaryTarget as? StorageTarget.KeePass)?.groupPath,
                mdbxDatabaseId = (primaryTarget as? StorageTarget.Mdbx)?.databaseId,
                bitwardenVaultId = (primaryTarget as? StorageTarget.Bitwarden)?.vaultId,
                bitwardenFolderId = (primaryTarget as? StorageTarget.Bitwarden)?.folderId,
                selectedStorageTargets = normalizedTargets,
                existingReplicaTargetKeys = existingTargetKeys,
                currentReplicaGroupId = replicaGroupId
            )
        }
    }

    fun addSelectedStorageTarget(target: StorageTarget) {
        val current = _uiState.value
        if (current.selectedStorageTargets.any { it.stableKey == target.stableKey }) return
        setSelectedStorageTargets(current.selectedStorageTargets.withStorageTargetSelected(target))
    }

    fun removeSelectedStorageTarget(target: StorageTarget) {
        val current = _uiState.value
        setSelectedStorageTargets(
            targets = current.selectedStorageTargets.withoutStorageTarget(target)
        )
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

    fun saveDraftImmediate(noteId: Long) {
        currentNoteId = noteId
        draftSaveJob?.cancel()
        val state = _uiState.value
        draftStore.saveDraft(noteId, state.title, state.contentField.text, state.tagsText)
    }

    fun restoreDraft(noteId: Long) {
        currentNoteId = noteId
        val draft = draftStore.loadDraft(noteId) ?: return
        val content = draft.content
        val hydratedField = TextFieldValue(content)
        val inlineImagePaths = NoteContentCodec.extractInlineImageIds(content)
        _uiState.update {
            it.copy(
                title = draft.title,
                contentField = hydratedField,
                tagsText = draft.tagsText,
                noteImagePaths = inlineImagePaths
            )
        }
    }

    fun clearDraft(noteId: Long) {
        draftSaveJob?.cancel()
        draftStore.clearDraft(noteId)
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

    private fun scheduleDraftSave() {
        draftSaveJob?.cancel()
        val draftNoteId = currentNoteId
        draftSaveJob = viewModelScope.launch {
            delay(AUTO_SAVE_DEBOUNCE_MS)
            val state = _uiState.value
            draftStore.saveDraft(draftNoteId, state.title, state.contentField.text, state.tagsText)
        }
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
        mdbxDatabaseId = mdbxDatabaseId,
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
