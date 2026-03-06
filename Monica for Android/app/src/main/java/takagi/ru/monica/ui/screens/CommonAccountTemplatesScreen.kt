package takagi.ru.monica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.data.CommonAccountPreferences
import takagi.ru.monica.data.CommonAccountTemplate

private data class TemplateTypeStyle(
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CommonAccountTemplatesScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { CommonAccountPreferences(context) }
    val templates by preferences.templatesFlow.collectAsState(initial = emptyList())

    val emailType = stringResource(R.string.common_account_type_email)
    val accountType = stringResource(R.string.common_account_type_account)
    val phoneType = stringResource(R.string.common_account_type_phone)
    val passwordType = stringResource(R.string.common_account_type_password)
    val allFilter = stringResource(R.string.filter_all)
    val typeOptions = listOf(emailType, accountType, phoneType, passwordType)

    var showEditor by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editingType by remember { mutableStateOf(accountType) }
    var editingTitle by remember { mutableStateOf("") }
    var editingContent by remember { mutableStateOf("") }
    var templateToDelete by remember { mutableStateOf<CommonAccountTemplate?>(null) }
    var selectedFilter by remember { mutableStateOf(allFilter) }

    fun normalizeType(raw: String): String {
        return when (raw) {
            emailType, accountType, phoneType, passwordType -> raw
            else -> accountType
        }
    }

    fun openCreateEditor(type: String = accountType) {
        editingId = null
        editingType = normalizeType(type)
        editingTitle = ""
        editingContent = ""
        showEditor = true
    }

    fun openEditEditor(template: CommonAccountTemplate) {
        editingId = template.id
        editingType = normalizeType(template.type)
        editingTitle = template.title
        editingContent = template.content
        showEditor = true
    }

    val filteredTemplates = remember(templates, selectedFilter, allFilter) {
        if (selectedFilter == allFilter) {
            templates
        } else {
            templates.filter { normalizeType(it.type) == selectedFilter }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.common_account_templates_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.go_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { openCreateEditor() },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )
                },
                text = { Text(stringResource(R.string.common_account_template_add)) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedFilter == allFilter,
                        onClick = { selectedFilter = allFilter },
                        label = { Text(allFilter) }
                    )
                    typeOptions.forEach { type ->
                        FilterChip(
                            selected = selectedFilter == type,
                            onClick = { selectedFilter = type },
                            label = { Text(type) },
                            leadingIcon = {
                                Icon(
                                    imageVector = when (type) {
                                        emailType -> Icons.Default.Email
                                        phoneType -> Icons.Default.Phone
                                        passwordType -> Icons.Default.Lock
                                        else -> Icons.Default.Person
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.common_account_templates_count, filteredTemplates.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (templates.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.common_account_templates_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.common_account_templates_empty_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(onClick = { openCreateEditor() }) {
                                Text(stringResource(R.string.common_account_template_add))
                            }
                        }
                    }
                }
            } else if (filteredTemplates.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.no_results),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(items = filteredTemplates, key = { it.id }) { template ->
                    CommonAccountTemplateCard(
                        template = template,
                        emailType = emailType,
                        accountType = accountType,
                        phoneType = phoneType,
                        passwordType = passwordType,
                        onEdit = { openEditEditor(template) },
                        onDelete = { templateToDelete = template }
                    )
                }
            }
        }
    }

    if (showEditor) {
        val isEditMode = editingId != null
        val isValid = editingTitle.isNotBlank() && editingContent.isNotBlank()

        ModalBottomSheet(
            onDismissRequest = { showEditor = false },
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            val style = templateTypeStyle(
                type = editingType,
                emailType = emailType,
                phoneType = phoneType,
                passwordType = passwordType
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(style.containerColor, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = style.icon,
                            contentDescription = null,
                            tint = style.contentColor
                        )
                    }
                    Column {
                        Text(
                            text = if (isEditMode) {
                                stringResource(R.string.common_account_template_edit)
                            } else {
                                stringResource(R.string.common_account_template_add)
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.common_account_templates_create_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.common_account_template_type),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    typeOptions.forEach { option ->
                        FilterChip(
                            selected = editingType == option,
                            onClick = { editingType = option },
                            label = { Text(option) },
                            leadingIcon = {
                                Icon(
                                    imageVector = when (option) {
                                        emailType -> Icons.Default.Email
                                        phoneType -> Icons.Default.Phone
                                        passwordType -> Icons.Default.Lock
                                        else -> Icons.Default.Person
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }

                OutlinedTextField(
                    value = editingTitle,
                    onValueChange = { editingTitle = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.common_account_template_title)) },
                    isError = editingTitle.isBlank()
                )

                OutlinedTextField(
                    value = editingContent,
                    onValueChange = { editingContent = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    label = { Text(stringResource(R.string.common_account_template_content)) },
                    isError = editingContent.isBlank()
                )

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.common_account_template_preview),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = editingTitle.ifBlank { stringResource(R.string.common_account_template_title) },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = editingContent.ifBlank { stringResource(R.string.common_account_template_content) },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = { showEditor = false },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        enabled = isValid,
                        onClick = {
                            scope.launch {
                                val id = editingId
                                if (id == null) {
                                    preferences.addTemplate(
                                        type = normalizeType(editingType),
                                        title = editingTitle,
                                        content = editingContent
                                    )
                                } else {
                                    preferences.upsertTemplate(
                                        CommonAccountTemplate(
                                            id = id,
                                            type = normalizeType(editingType),
                                            title = editingTitle,
                                            content = editingContent
                                        )
                                    )
                                }
                                showEditor = false
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }

    templateToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { templateToDelete = null },
            title = { Text(stringResource(R.string.common_account_template_delete_title)) },
            text = { Text(target.title.ifBlank { stringResource(R.string.common_account_template_delete_message) }) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            preferences.deleteTemplate(target.id)
                            templateToDelete = null
                        }
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { templateToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun CommonAccountTemplateCard(
    template: CommonAccountTemplate,
    emailType: String,
    accountType: String,
    phoneType: String,
    passwordType: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val normalizedType = when (template.type) {
        emailType, accountType, phoneType, passwordType -> template.type
        else -> accountType
    }
    val style = templateTypeStyle(
        type = normalizedType,
        emailType = emailType,
        phoneType = phoneType,
        passwordType = passwordType
    )

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(),
        elevation = CardDefaults.cardElevation(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(style.containerColor, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = style.icon,
                    contentDescription = null,
                    tint = style.contentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = template.title.ifBlank { stringResource(R.string.common_account_template_title) },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.edit),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Text(
                    text = template.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun templateTypeStyle(
    type: String,
    emailType: String,
    phoneType: String,
    passwordType: String
): TemplateTypeStyle {
    return when (type) {
        emailType -> TemplateTypeStyle(
            icon = Icons.Default.Email,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
        phoneType -> TemplateTypeStyle(
            icon = Icons.Default.Phone,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
        passwordType -> TemplateTypeStyle(
            icon = Icons.Default.Lock,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
        else -> TemplateTypeStyle(
            icon = Icons.Default.Person,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}
