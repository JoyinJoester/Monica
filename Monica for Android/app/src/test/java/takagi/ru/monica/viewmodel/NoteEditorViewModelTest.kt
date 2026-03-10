package takagi.ru.monica.viewmodel

import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.notes.domain.NoteContentCodec
import takagi.ru.monica.utils.RememberedStorageTarget
import java.util.Date

class NoteEditorViewModelTest {

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
            initialBitwardenVaultId = null,
            initialBitwardenFolderId = null,
            draftStorageTarget = NoteDraftStorageTarget(
                categoryId = 2L,
                keepassDatabaseId = 3L
            ),
            rememberedStorageTarget = RememberedStorageTarget(
                categoryId = 4L,
                keepassDatabaseId = 5L,
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
}
