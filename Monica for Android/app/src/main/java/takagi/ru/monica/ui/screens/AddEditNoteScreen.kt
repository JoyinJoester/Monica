package takagi.ru.monica.ui.screens

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.LocalKeePassDatabase
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.bitwarden.repository.BitwardenRepository
import takagi.ru.monica.notes.domain.NoteContentCodec
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.ui.components.ImageDialog
import takagi.ru.monica.ui.components.M3IdentityVerifyDialog
import takagi.ru.monica.ui.components.StorageTargetSelectorCard
import takagi.ru.monica.util.ImageManager
import takagi.ru.monica.util.PhotoPickerHelper
import takagi.ru.monica.utils.BiometricHelper
import takagi.ru.monica.utils.RememberedStorageTarget
import takagi.ru.monica.utils.SettingsManager
import takagi.ru.monica.viewmodel.NoteViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditNoteScreen(
    noteId: Long,
    onNavigateBack: () -> Unit,
    initialCategoryId: Long? = null,
    initialKeePassDatabaseId: Long? = null,
    initialBitwardenVaultId: Long? = null,
    initialBitwardenFolderId: String? = null,
    viewModel: NoteViewModel = viewModel()
) {
    val context = LocalContext.current
    val biometricHelper = remember { BiometricHelper(context) }
    val securityManager = remember { SecurityManager(context) }
    val settingsManager = remember { SettingsManager(context) }
    val appSettings by settingsManager.settingsFlow.collectAsState(
        initial = AppSettings(biometricEnabled = false)
    )

    // 重构: 显式区分 Title 和 Content
    var title by rememberSaveable { mutableStateOf("") }
    var contentField by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var isMarkdownPreview by rememberSaveable { mutableStateOf(false) }
    var tagsText by rememberSaveable { mutableStateOf("") }
    var isFavorite by rememberSaveable { mutableStateOf(false) }
    
    // 重构: 支持多图
    val noteImagePaths = rememberSaveable(saver = listSaver(
        save = { it.toList() },
        restore = { it.toMutableStateList() }
    )) { mutableStateListOf<String>() }
    // 待删除的图片列表（只有在保存时才真正删除文件，防止误删或取消时丢失）
    val deletedImagePaths = rememberSaveable(saver = listSaver(
        save = { it.toList() },
        restore = { it.toMutableStateList() }
    )) { mutableStateListOf<String>() }
    // 图片位图缓存
    val noteImageBitmaps = remember { mutableStateMapOf<String, Bitmap>() }
    
    var showNoteImageDialog by remember { mutableStateOf<String?>(null) } // 存文件名
    var selectedCategoryId by rememberSaveable(noteId) { mutableStateOf<Long?>(null) }
    var keepassDatabaseId by rememberSaveable(noteId) { mutableStateOf<Long?>(null) }
    var bitwardenVaultId by rememberSaveable(noteId) { mutableStateOf<Long?>(null) }
    var bitwardenFolderId by rememberSaveable(noteId) { mutableStateOf<String?>(null) }
    var hasAppliedInitialStorage by rememberSaveable(noteId) { mutableStateOf(false) }
    var isSaving by rememberSaveable { mutableStateOf(false) }
    var createdAt by remember { mutableStateOf(java.util.Date()) }
    var currentNote by remember { mutableStateOf<SecureItem?>(null) }

    var showConfirmDelete by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var masterPassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf(false) }
    var showAddImageDialog by remember { mutableStateOf(false) }
    var pendingImageInsertionCursor by rememberSaveable { mutableStateOf(-1) }

    val scope = rememberCoroutineScope()
    val imageManager = remember { ImageManager(context) }
    val activity = remember(context) { context as? Activity }
    val isEditing = noteId != -1L
    val isBitwardenNoteTarget = bitwardenVaultId != null
    val isMarkdown = true
    
    val inlineImageIds = remember(contentField.text) { NoteContentCodec.extractInlineImageIds(contentField.text) }
    // 只要有标题、内容或者有图片就可以保存
    val canSave = title.isNotBlank() || contentField.text.isNotBlank() || inlineImageIds.isNotEmpty()
    
    val database = remember { PasswordDatabase.getDatabase(context) }
    val categories by database.categoryDao().getAllCategories().collectAsState(initial = emptyList())
    val keepassDatabases by database.localKeePassDatabaseDao().getAllDatabases().collectAsState(initial = emptyList())
    val bitwardenVaults by database.bitwardenVaultDao().getAllVaultsFlow().collectAsState(initial = emptyList())
    val draftStorageTarget by viewModel.draftStorageTarget.collectAsState()
    val rememberedStorageTarget by settingsManager
        .rememberedStorageTargetFlow(SettingsManager.StorageTargetScope.NOTE)
        .collectAsState(initial = null as RememberedStorageTarget?)
    
    LaunchedEffect(noteId) {
        if (!isEditing) return@LaunchedEffect
        val note = viewModel.getNoteById(noteId)
        note?.let {
            val draft = it.toNoteEditDraft()
            currentNote = it

            title = draft.title
            val hydratedContent = NoteContentCodec.appendInlineImageRefs(
                content = draft.content.trimEnd(),
                imageIds = draft.imagePaths
            )
            contentField = TextFieldValue(hydratedContent)
            isMarkdownPreview = false
            tagsText = draft.tags.joinToString(", ")
            isFavorite = draft.isFavorite
            selectedCategoryId = draft.categoryId
            keepassDatabaseId = draft.keepassDatabaseId
            bitwardenVaultId = draft.bitwardenVaultId
            bitwardenFolderId = draft.bitwardenFolderId
            noteImagePaths.clear()
            noteImagePaths.addAll(NoteContentCodec.extractInlineImageIds(contentField.text))
            createdAt = draft.createdAt
        }
    }

    LaunchedEffect(
        isEditing,
        hasAppliedInitialStorage,
        initialCategoryId,
        initialKeePassDatabaseId,
        initialBitwardenVaultId,
        initialBitwardenFolderId,
        draftStorageTarget,
        rememberedStorageTarget
    ) {
        if (isEditing || hasAppliedInitialStorage) return@LaunchedEffect
        val remembered = rememberedStorageTarget ?: return@LaunchedEffect
        selectedCategoryId = initialCategoryId ?: draftStorageTarget.categoryId ?: remembered.categoryId
        keepassDatabaseId = initialKeePassDatabaseId ?: draftStorageTarget.keepassDatabaseId ?: remembered.keepassDatabaseId
        bitwardenVaultId = initialBitwardenVaultId ?: draftStorageTarget.bitwardenVaultId ?: remembered.bitwardenVaultId
        bitwardenFolderId = initialBitwardenFolderId ?: draftStorageTarget.bitwardenFolderId ?: remembered.bitwardenFolderId
        hasAppliedInitialStorage = true
    }

    LaunchedEffect(contentField.text) {
        val inlineImagePaths = NoteContentCodec.extractInlineImageIds(contentField.text)
        val currentPaths = noteImagePaths.toList()
        val removed = currentPaths.filterNot { inlineImagePaths.contains(it) }
        if (removed.isNotEmpty()) {
            removed.forEach { removedPath ->
                if (!deletedImagePaths.contains(removedPath)) {
                    deletedImagePaths.add(removedPath)
                }
            }
        }
        val reAdded = deletedImagePaths.filter { inlineImagePaths.contains(it) }
        if (reAdded.isNotEmpty()) {
            deletedImagePaths.removeAll(reAdded.toSet())
        }
        if (inlineImagePaths != currentPaths) {
            noteImagePaths.clear()
            noteImagePaths.addAll(inlineImagePaths)
        }
    }

    // 重构: 加载图片列表
    LaunchedEffect(noteImagePaths.toList()) {
        noteImagePaths.forEach { fileName ->
            if (!noteImageBitmaps.containsKey(fileName)) {
                val bitmap = imageManager.loadImage(fileName)
                if (bitmap != null) {
                    noteImageBitmaps[fileName] = bitmap
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        PhotoPickerHelper.setCallback(context, object : PhotoPickerHelper.PhotoPickerCallback {
            override fun onPhotoSelected(imagePath: String?) {
                imagePath?.let { path ->
                    scope.launch {
                        try {
                            if (isBitwardenNoteTarget) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.note_bitwarden_inline_image_disabled_toast),
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@launch
                            }
                            val file = java.io.File(path)
                            if (!file.exists() || file.length() == 0L) {
                                Toast.makeText(context, context.getString(R.string.photo_file_missing_or_empty), Toast.LENGTH_SHORT).show()
                                return@launch
                            }

                            val fileName = imageManager.saveImageFromUri(Uri.fromFile(file))
                            if (fileName != null) {
                                isMarkdownPreview = false
                                contentField = insertInlineImageAtSelection(
                                    current = contentField,
                                    imageId = fileName,
                                    insertionIndex = pendingImageInsertionCursor.takeIf { it >= 0 }
                                )
                                pendingImageInsertionCursor = -1
                                deletedImagePaths.remove(fileName)
                                file.delete()
                            } else {
                                Toast.makeText(context, context.getString(R.string.photo_save_failed), Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.photo_process_failed, e.message ?: e.javaClass.simpleName),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }

            override fun onError(error: String) {
                pendingImageInsertionCursor = -1
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            }
        })
    }

    fun saveNote() {
        if (isSaving || !canSave) return
        isSaving = true
        val payload = buildNoteSavePayload(
            title = title,
            content = contentField.text,
            isMarkdown = isMarkdown,
            tagsText = tagsText
        )
        
        if (isEditing) {
            viewModel.updateNote(
                id = noteId,
                content = payload.content,
                title = payload.title,
                tags = payload.tags,
                isMarkdown = payload.isMarkdown,
                isFavorite = isFavorite,
                createdAt = createdAt,
                categoryId = selectedCategoryId,
                imagePaths = payload.imagePathsJson,
                keepassDatabaseId = keepassDatabaseId,
                bitwardenVaultId = bitwardenVaultId,
                bitwardenFolderId = bitwardenFolderId
            )
        } else {
            viewModel.addNote(
                content = payload.content,
                title = payload.title,
                tags = payload.tags,
                isMarkdown = payload.isMarkdown,
                isFavorite = isFavorite,
                categoryId = selectedCategoryId,
                imagePaths = payload.imagePathsJson,
                keepassDatabaseId = keepassDatabaseId,
                bitwardenVaultId = bitwardenVaultId,
                bitwardenFolderId = bitwardenFolderId
            )
        }
        if (deletedImagePaths.isNotEmpty()) {
            viewModel.cleanupUnreferencedNoteImages(deletedImagePaths.toList())
        }
        scope.launch {
            settingsManager.updateRememberedStorageTarget(
                scope = SettingsManager.StorageTargetScope.NOTE,
                target = RememberedStorageTarget(
                    categoryId = selectedCategoryId,
                    keepassDatabaseId = keepassDatabaseId,
                    bitwardenVaultId = bitwardenVaultId,
                    bitwardenFolderId = bitwardenFolderId
                )
            )
        }
        onNavigateBack()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (isEditing) R.string.edit_note else R.string.new_note
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { isFavorite = !isFavorite }) {
                        Icon(
                            imageVector = if (isFavorite) {
                                Icons.Default.Favorite
                            } else {
                                Icons.Default.FavoriteBorder
                            },
                            contentDescription = stringResource(R.string.favorite)
                        )
                    }
                    if (isEditing) {
                        IconButton(onClick = { showConfirmDelete = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { saveNote() },
                containerColor = if (canSave && !isSaving) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (canSave && !isSaving) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.save)
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            StorageTargetSelectorCard(
                keepassDatabases = keepassDatabases,
                selectedKeePassDatabaseId = keepassDatabaseId,
                onKeePassDatabaseSelected = {
                    keepassDatabaseId = it
                    if (it != null) {
                        bitwardenVaultId = null
                        bitwardenFolderId = null
                    }
                },
                bitwardenVaults = bitwardenVaults,
                selectedBitwardenVaultId = bitwardenVaultId,
                onBitwardenVaultSelected = {
                    bitwardenVaultId = it
                    if (it != null) keepassDatabaseId = null
                },
                categories = categories,
                selectedCategoryId = selectedCategoryId,
                onCategorySelected = { selectedCategoryId = it },
                selectedBitwardenFolderId = bitwardenFolderId,
                onBitwardenFolderSelected = { folderId ->
                    bitwardenFolderId = folderId
                    if (bitwardenVaultId != null) keepassDatabaseId = null
                }
            )

            NoteEditorSection(
                title = title,
                onTitleChange = { title = it },
                content = contentField,
                onContentChange = { contentField = it },
                isMarkdownPreview = isMarkdownPreview,
                onMarkdownPreviewModeChange = { isMarkdownPreview = it },
                inlineImageBitmaps = noteImageBitmaps,
                onPreviewInlineImage = { fileName -> showNoteImageDialog = fileName },
                tagsText = tagsText,
                onTagsTextChange = { tagsText = it }
            )

            NoteImagesSection(
                noteImagePaths = noteImagePaths,
                noteImageBitmaps = noteImageBitmaps,
                imageActionsEnabled = !isBitwardenNoteTarget,
                disabledReason = if (isBitwardenNoteTarget) {
                    stringResource(R.string.note_bitwarden_inline_image_disabled_reason)
                } else {
                    null
                },
                onAddImageClick = {
                    if (isBitwardenNoteTarget) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.note_bitwarden_inline_image_disabled_toast),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        pendingImageInsertionCursor = contentField.selection.start.coerceIn(0, contentField.text.length)
                        showAddImageDialog = true
                    }
                },
                onPreviewImage = { fileName -> showNoteImageDialog = fileName },
                onRemoveImage = { fileName ->
                    if (isBitwardenNoteTarget) return@NoteImagesSection
                    val updated = NoteContentCodec.removeInlineImageRef(contentField.text, fileName)
                    contentField = contentField.copy(
                        text = updated,
                        selection = TextRange(updated.length)
                    )
                }
            )
        }
    }

    // Image Detail Dialog
    if (showNoteImageDialog != null) {
        val bitmap = noteImageBitmaps[showNoteImageDialog!!]
        if (bitmap != null) {
            ImageDialog(
                bitmap = bitmap,
                onDismiss = { showNoteImageDialog = null }
            )
        } else {
            showNoteImageDialog = null
        }
    }
    
    // Add Image Selection Dialog (Bottom Sheet or Dialog)
    if (showAddImageDialog) {
        AlertDialog(
            onDismissRequest = { showAddImageDialog = false },
            icon = { Icon(Icons.Default.Image, contentDescription = null) },
            title = { Text(stringResource(R.string.section_photos)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(stringResource(R.string.note_image_source_title), style = MaterialTheme.typography.bodyMedium)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                showAddImageDialog = false
                                if (activity != null) {
                                    pendingImageInsertionCursor = contentField.selection.start.coerceIn(0, contentField.text.length)
                                    PhotoPickerHelper.currentTag = "note"
                                    PhotoPickerHelper.pickFromGallery(activity)
                                } else {
                                    Toast.makeText(context, context.getString(R.string.photo_cannot_open_gallery), Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Image, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.gallery))
                        }
                        OutlinedButton(
                            onClick = {
                                showAddImageDialog = false
                                if (activity != null) {
                                    pendingImageInsertionCursor = contentField.selection.start.coerceIn(0, contentField.text.length)
                                    PhotoPickerHelper.currentTag = "note"
                                    PhotoPickerHelper.takePhoto(activity)
                                } else {
                                    Toast.makeText(context, context.getString(R.string.photo_cannot_open_camera_use_gallery), Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.camera))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    showAddImageDialog = false
                    pendingImageInsertionCursor = -1
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.delete_note_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDelete = false
                    showPasswordDialog = true
                }) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    fun performDelete() {
        scope.launch {
            currentNote?.let { note ->
                viewModel.deleteNote(note)
            }
            showPasswordDialog = false
            masterPassword = ""
            passwordError = false
            onNavigateBack()
        }
    }

    if (showPasswordDialog) {
        val activity = context as? FragmentActivity
        val biometricAction = if (
            activity != null &&
            appSettings.biometricEnabled &&
            biometricHelper.isBiometricAvailable()
        ) {
            {
                biometricHelper.authenticate(
                    activity = activity,
                    title = context.getString(R.string.verify_identity),
                    subtitle = context.getString(R.string.verify_to_delete),
                    onSuccess = { performDelete() },
                    onError = { error ->
                        android.widget.Toast.makeText(
                            context,
                            error,
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    },
                    onFailed = {}
                )
            }
        } else {
            null
        }
        M3IdentityVerifyDialog(
            title = stringResource(R.string.verify_identity),
            message = stringResource(R.string.verify_to_delete),
            passwordValue = masterPassword,
            onPasswordChange = {
                masterPassword = it
                passwordError = false
            },
            onDismiss = {
                showPasswordDialog = false
                masterPassword = ""
                passwordError = false
            },
            onConfirm = {
                if (securityManager.verifyMasterPassword(masterPassword)) {
                    performDelete()
                } else {
                    passwordError = true
                }
            },
            confirmText = stringResource(R.string.delete),
            destructiveConfirm = true,
            isPasswordError = passwordError,
            passwordErrorText = stringResource(R.string.current_password_incorrect),
            onBiometricClick = biometricAction,
            biometricHintText = if (biometricAction == null) {
                context.getString(R.string.biometric_not_available)
            } else {
                null
            }
        )
    }
}

private data class NoteEditDraft(
    val title: String,
    val content: String,
    val isMarkdown: Boolean,
    val tags: List<String>,
    val isFavorite: Boolean,
    val categoryId: Long?,
    val keepassDatabaseId: Long?,
    val bitwardenVaultId: Long?,
    val bitwardenFolderId: String?,
    val imagePaths: List<String>,
    val createdAt: java.util.Date
)

private data class NoteSavePayload(
    val title: String,
    val content: String,
    val imagePathsJson: String,
    val isMarkdown: Boolean,
    val tags: List<String>
)

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
        bitwardenVaultId = bitwardenVaultId,
        bitwardenFolderId = bitwardenFolderId,
        imagePaths = NoteContentCodec.decodeImagePaths(imagePaths),
        createdAt = createdAt
    )
}

private fun buildNoteSavePayload(
    title: String,
    content: String,
    isMarkdown: Boolean,
    tagsText: String
): NoteSavePayload {
    val tags = tagsText
        .split(',', '\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
    val normalizedContent = content.trimEnd()
    val finalTitle = if (title.isNotBlank()) {
        title.trim()
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
