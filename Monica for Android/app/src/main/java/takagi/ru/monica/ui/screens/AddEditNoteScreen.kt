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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import takagi.ru.monica.R
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.LocalKeePassDatabase
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.bitwarden.repository.BitwardenRepository
import takagi.ru.monica.data.model.NoteData
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.ui.components.ImageDialog
import takagi.ru.monica.ui.components.M3IdentityVerifyDialog
import takagi.ru.monica.ui.components.StorageTargetSelectorCard
import takagi.ru.monica.util.ImageManager
import takagi.ru.monica.util.PhotoPickerHelper
import takagi.ru.monica.utils.BiometricHelper
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

    var title by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    var isFavorite by rememberSaveable { mutableStateOf(false) }
    var noteImageFileName by rememberSaveable { mutableStateOf<String?>(null) }
    var noteImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showNoteImageDialog by remember { mutableStateOf(false) }
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

    val scope = rememberCoroutineScope()
    val imageManager = remember { ImageManager(context) }
    val activity = remember(context) { context as? Activity }
    val isEditing = noteId != -1L
    val canSave = title.isNotBlank() || content.isNotBlank() || !noteImageFileName.isNullOrBlank()
    val database = remember { PasswordDatabase.getDatabase(context) }
    val categories by database.categoryDao().getAllCategories().collectAsState(initial = emptyList())
    val keepassDatabases by database.localKeePassDatabaseDao().getAllDatabases().collectAsState(initial = emptyList())
    val bitwardenRepository = remember { BitwardenRepository.getInstance(context) }
    var bitwardenVaults by remember { mutableStateOf<List<BitwardenVault>>(emptyList()) }
    val draftStorageTarget by viewModel.draftStorageTarget.collectAsState()
    LaunchedEffect(Unit) {
        bitwardenVaults = bitwardenRepository.getAllVaults()
    }

    LaunchedEffect(noteId) {
        if (!isEditing) return@LaunchedEffect
        val note = viewModel.getNoteById(noteId)
        note?.let {
            currentNote = it
            title = it.title
            content = try {
                val noteData = Json.decodeFromString<NoteData>(it.itemData)
                noteData.content
            } catch (_: Exception) {
                it.notes
            }
            isFavorite = it.isFavorite
            selectedCategoryId = it.categoryId
            keepassDatabaseId = it.keepassDatabaseId
            bitwardenVaultId = it.bitwardenVaultId
            bitwardenFolderId = it.bitwardenFolderId
            noteImageFileName = try {
                if (it.imagePaths.isNotBlank()) {
                    val paths = Json.decodeFromString<List<String>>(it.imagePaths)
                    paths.firstOrNull()?.takeIf { path -> path.isNotBlank() }
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
            createdAt = it.createdAt
        }
    }

    LaunchedEffect(
        isEditing,
        hasAppliedInitialStorage,
        initialCategoryId,
        initialKeePassDatabaseId,
        initialBitwardenVaultId,
        initialBitwardenFolderId,
        draftStorageTarget
    ) {
        if (isEditing || hasAppliedInitialStorage) return@LaunchedEffect
        selectedCategoryId = initialCategoryId ?: draftStorageTarget.categoryId
        keepassDatabaseId = initialKeePassDatabaseId ?: draftStorageTarget.keepassDatabaseId
        bitwardenVaultId = initialBitwardenVaultId ?: draftStorageTarget.bitwardenVaultId
        bitwardenFolderId = initialBitwardenFolderId ?: draftStorageTarget.bitwardenFolderId
        hasAppliedInitialStorage = true
    }

    LaunchedEffect(noteImageFileName) {
        noteImageBitmap = noteImageFileName?.let { imageManager.loadImage(it) }
    }

    LaunchedEffect(Unit) {
        PhotoPickerHelper.setCallback(context, object : PhotoPickerHelper.PhotoPickerCallback {
            override fun onPhotoSelected(imagePath: String?) {
                imagePath?.let { path ->
                    scope.launch {
                        try {
                            val file = java.io.File(path)
                            if (!file.exists() || file.length() == 0L) {
                                Toast.makeText(context, context.getString(R.string.photo_file_missing_or_empty), Toast.LENGTH_SHORT).show()
                                return@launch
                            }

                            val fileName = imageManager.saveImageFromUri(Uri.fromFile(file))
                            if (fileName != null) {
                                noteImageFileName?.let { oldFile ->
                                    if (oldFile != fileName) {
                                        imageManager.deleteImage(oldFile)
                                    }
                                }
                                noteImageFileName = fileName
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
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            }
        })
    }

    fun saveNote() {
        if (isSaving || !canSave) return
        isSaving = true
        val normalizedTitle = title.trim().takeIf { it.isNotEmpty() }
        val normalizedContent = content.trimEnd()
        val imagePathsJson = Json.encodeToString(listOf(noteImageFileName ?: ""))
        if (isEditing) {
            viewModel.updateNote(
                id = noteId,
                content = normalizedContent,
                title = normalizedTitle,
                isFavorite = isFavorite,
                createdAt = createdAt,
                categoryId = selectedCategoryId,
                imagePaths = imagePathsJson,
                keepassDatabaseId = keepassDatabaseId,
                bitwardenVaultId = bitwardenVaultId,
                bitwardenFolderId = bitwardenFolderId
            )
        } else {
            viewModel.addNote(
                content = normalizedContent,
                title = normalizedTitle,
                isFavorite = isFavorite,
                categoryId = selectedCategoryId,
                imagePaths = imagePathsJson,
                keepassDatabaseId = keepassDatabaseId,
                bitwardenVaultId = bitwardenVaultId,
                bitwardenFolderId = bitwardenFolderId
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

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.title)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.section_photos),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (noteImageBitmap != null) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = noteImageBitmap!!.asImageBitmap(),
                                contentDescription = stringResource(R.string.view_image),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 160.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { showNoteImageDialog = true },
                                contentScale = ContentScale.Crop
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    noteImageFileName?.let { fileName ->
                                        imageManager.deleteImage(fileName)
                                    }
                                    noteImageFileName = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.delete_image))
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = stringResource(R.string.no_image),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (activity != null) {
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
                                if (activity != null) {
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
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.content),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp),
                        placeholder = { Text(stringResource(R.string.note_placeholder)) },
                        minLines = 8,
                        maxLines = 20,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }
    }

    if (showNoteImageDialog && noteImageBitmap != null) {
        ImageDialog(
            bitmap = noteImageBitmap!!,
            onDismiss = { showNoteImageDialog = false }
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
