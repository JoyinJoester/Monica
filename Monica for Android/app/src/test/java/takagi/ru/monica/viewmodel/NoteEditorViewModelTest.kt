package takagi.ru.monica.viewmodel

import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.notes.domain.NoteContentCodec
import takagi.ru.monica.notes.domain.NoteDraftStorage
import takagi.ru.monica.notes.domain.NoteDraftStore
import takagi.ru.monica.utils.RememberedStorageTarget
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class NoteEditorViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadForEdit_hydratesLegacyImagesIntoMarkdownAndState() {
        val vm = NoteEditorViewModel()
        val note = createNote(
            title = "Legacy",
            content = "hello",
            tags = listOf("one", "two"),
            imageIds = listOf("img-1")
        )

        vm.loadForEdit(note)
        val state = vm.uiState.value

        assertEquals("Legacy", state.title)
        assertTrue(state.contentField.text.contains("![](monica-image://img-1)"))
        assertEquals(listOf("img-1"), state.noteImagePaths)
        assertTrue(state.deletedImagePaths.isEmpty())
        assertEquals("one, two", state.tagsText)
    }

    @Test
    fun updateContent_tracksDeletedImages_andRecoversWhenAddedBack() {
        val vm = NoteEditorViewModel()
        vm.loadForEdit(
            createNote(
                content = "hello\n\n![](monica-image://img-1)",
                imageIds = listOf("img-1")
            )
        )

        vm.updateContent(TextFieldValue("hello"))
        assertEquals(listOf("img-1"), vm.uiState.value.deletedImagePaths)
        assertTrue(vm.uiState.value.noteImagePaths.isEmpty())

        vm.updateContent(TextFieldValue("hello\n\n![](monica-image://img-1)"))
        assertTrue(vm.uiState.value.deletedImagePaths.isEmpty())
        assertEquals(listOf("img-1"), vm.uiState.value.noteImagePaths)
    }

    @Test
    fun insertInlineImage_turnsOffPreview_andAddsImageRef() {
        val vm = NoteEditorViewModel()
        vm.updateContent(TextFieldValue("alpha"))
        vm.updatePreviewMode(true)

        vm.insertInlineImage(imageId = "img-2", insertionIndex = null)
        val state = vm.uiState.value

        assertFalse(state.isMarkdownPreview)
        assertTrue(state.contentField.text.contains("![](monica-image://img-2)"))
        assertEquals(listOf("img-2"), state.noteImagePaths)
    }

    @Test
    fun buildSavePayload_usesFallbackTitle_andNormalizesTags() {
        val vm = NoteEditorViewModel()
        vm.updateTitle("")
        vm.updateContent(TextFieldValue("first line\nsecond"))
        vm.updateTagsText("a, b , a\nc")

        val payload = vm.buildSavePayload(isMarkdown = true)

        assertEquals("first line", payload.title)
        assertEquals(listOf("a", "b", "c"), payload.tags)
        assertTrue(payload.isMarkdown)
        assertEquals("first line\nsecond", payload.content)
        assertEquals("[]", payload.imagePathsJson)
    }

    @Test
    fun applyInitialStorageIfNeeded_prefersInitialThenDraftThenRemembered() {
        val vm = NoteEditorViewModel()
        vm.applyInitialStorageIfNeeded(
            isEditing = false,
            initialCategoryId = 1L,
            initialKeePassDatabaseId = null,
            initialKeePassGroupPath = null,
            initialMdbxDatabaseId = null,
            initialBitwardenVaultId = null,
            initialBitwardenFolderId = null,
            draftStorageTarget = NoteDraftStorageTarget(
                categoryId = 2L,
                keepassDatabaseId = 3L
            ),
            rememberedStorageTarget = RememberedStorageTarget(
                categoryId = 4L,
                keepassDatabaseId = 5L,
                keepassGroupPath = null,
                bitwardenVaultId = 6L,
                bitwardenFolderId = "f-1"
            )
        )

        val state = vm.uiState.value
        assertEquals(1L, state.selectedCategoryId)
        assertEquals(3L, state.keepassDatabaseId)
        assertEquals(6L, state.bitwardenVaultId)
        assertEquals("f-1", state.bitwardenFolderId)
        assertTrue(state.hasAppliedInitialStorage)
    }

    @Test
    fun tryStartSaving_requiresContentAndRespectsInFlightFlag() {
        val vm = NoteEditorViewModel()
        assertFalse(vm.tryStartSaving())

        vm.updateTitle("x")
        assertTrue(vm.tryStartSaving())
        assertFalse(vm.tryStartSaving())

        vm.stopSaving()
        assertTrue(vm.tryStartSaving())
    }

    @Test
    fun restoreDraft_loadsSharedDraftForNewNoteAfterUserConfirms() {
        val drafts = FakeNoteDraftStorage().apply {
            saveDraft(
                noteId = -1L,
                title = "Previous editor",
                content = "stale content",
                tagsText = "old"
            )
        }
        val vm = NoteEditorViewModel { drafts }

        vm.resetForNewNote()
        vm.restoreDraft(-1L)

        val state = vm.uiState.value
        assertEquals("Previous editor", state.title)
        assertEquals("stale content", state.contentField.text)
        assertEquals("old", state.tagsText)
    }

    @Test
    fun saveDraftImmediate_persistsSharedDraftForNewNote() {
        val drafts = FakeNoteDraftStorage()
        val vm = NoteEditorViewModel { drafts }

        vm.resetForNewNote()
        vm.updateTitle("Unsaved new note")
        vm.updateContent(TextFieldValue("new content"))
        vm.saveDraftImmediate(-1L)

        val draft = drafts.loadDraft(-1L)
        assertTrue(drafts.hasDraft(-1L))
        assertEquals("Unsaved new note", draft?.title)
        assertEquals("new content", draft?.content)
    }

    private fun createNote(
        title: String = "t",
        content: String = "c",
        tags: List<String> = emptyList(),
        imageIds: List<String> = emptyList()
    ): SecureItem {
        val (itemData, notes) = NoteContentCodec.encode(
            content = content,
            tags = tags,
            isMarkdown = true
        )
        return SecureItem(
            id = 7L,
            itemType = ItemType.NOTE,
            title = title,
            notes = notes,
            itemData = itemData,
            imagePaths = NoteContentCodec.encodeImagePaths(imageIds),
            createdAt = Date(1700000000000L),
            updatedAt = Date(1700000000000L)
        )
    }

    private class FakeNoteDraftStorage : NoteDraftStorage {
        private val drafts = mutableMapOf<Long, NoteDraftStore.NoteDraft>()

        override fun saveDraft(noteId: Long, title: String, content: String, tagsText: String) {
            if (title.isBlank() && content.isBlank() && tagsText.isBlank()) {
                clearDraft(noteId)
            } else {
                drafts[noteId] = NoteDraftStore.NoteDraft(
                    title = title,
                    content = content,
                    tagsText = tagsText
                )
            }
        }

        override fun loadDraft(noteId: Long): NoteDraftStore.NoteDraft? = drafts[noteId]

        override fun clearDraft(noteId: Long) {
            drafts.remove(noteId)
        }

        override fun hasDraft(noteId: Long): Boolean = drafts.containsKey(noteId)
    }
}
