package takagi.ru.monica.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import takagi.ru.monica.R

/**
 * 密保问题设置页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityQuestionScreen(
    hasExistingQuestions: Boolean,
    onNavigateBack: () -> Unit,
    onSaveQuestions: (List<Pair<String, String>>) -> Unit
) {
    var question1 by remember { mutableStateOf("") }
    var answer1 by remember { mutableStateOf("") }
    
    var question2 by remember { mutableStateOf("") }
    var answer2 by remember { mutableStateOf("") }
    
    var question3 by remember { mutableStateOf("") }
    var answer3 by remember { mutableStateOf("") }
    
    var errorMessage by remember { mutableStateOf("") }
    var showSuccessDialog by remember { mutableStateOf(false) }
    
    val scrollState = rememberScrollState()
    
    // 预设问题列表
    val presetQuestions = listOf(
        stringResource(R.string.security_question_preset_1),
        stringResource(R.string.security_question_preset_2),
        stringResource(R.string.security_question_preset_3),
        stringResource(R.string.security_question_preset_4),
        stringResource(R.string.security_question_preset_5),
        stringResource(R.string.security_question_preset_6),
        stringResource(R.string.security_question_preset_7),
        stringResource(R.string.security_question_preset_8),
        stringResource(R.string.security_question_preset_9),
        stringResource(R.string.security_question_preset_10)
    )
    
    // 提前获取所有错误消息
    val errorQ1Empty = stringResource(R.string.security_question_error_q1_empty) + " & " + stringResource(R.string.security_question_error_a1_empty)
    val errorQ2Empty = stringResource(R.string.security_question_error_q2_empty) + " & " + stringResource(R.string.security_question_error_a2_empty)
    val errorQ3Empty = stringResource(R.string.security_question_error_q3_empty) + " & " + stringResource(R.string.security_question_error_a3_empty)
    val errorDuplicate = stringResource(R.string.security_question_error_duplicate)
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.security_question_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.return_text))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 说明卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Column {
                        Text(
                            stringResource(R.string.security_question_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.security_question_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 问题1
            Text(
                stringResource(R.string.security_question_label_1),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            var expanded1 by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded1,
                onExpandedChange = { expanded1 = it }
            ) {
                OutlinedTextField(
                    value = question1,
                    onValueChange = { 
                        question1 = it
                        errorMessage = ""
                    },
                    label = { Text(stringResource(R.string.security_question_select)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded1)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                )
                
                ExposedDropdownMenu(
                    expanded = expanded1,
                    onDismissRequest = { expanded1 = false }
                ) {
                    presetQuestions.forEach { question ->
                        DropdownMenuItem(
                            text = { Text(question) },
                            onClick = {
                                question1 = question
                                expanded1 = false
                            }
                        )
                    }
                }
            }
            
            OutlinedTextField(
                value = answer1,
                onValueChange = { 
                    answer1 = it
                    errorMessage = ""
                },
                label = { Text(stringResource(R.string.security_question_answer)) },
                leadingIcon = {
                    Icon(Icons.Default.QuestionAnswer, contentDescription = null)
                },
                placeholder = { Text(stringResource(R.string.security_question_answer_hint)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                singleLine = true
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            // 问题2
            Text(
                stringResource(R.string.security_question_label_2),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            var expanded2 by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded2,
                onExpandedChange = { expanded2 = it }
            ) {
                OutlinedTextField(
                    value = question2,
                    onValueChange = { 
                        question2 = it
                        errorMessage = ""
                    },
                    label = { Text(stringResource(R.string.security_question_select)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded2)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                )
                
                ExposedDropdownMenu(
                    expanded = expanded2,
                    onDismissRequest = { expanded2 = false }
                ) {
                    presetQuestions.forEach { question ->
                        DropdownMenuItem(
                            text = { Text(question) },
                            onClick = {
                                question2 = question
                                expanded2 = false
                            }
                        )
                    }
                }
            }
            
            OutlinedTextField(
                value = answer2,
                onValueChange = { 
                    answer2 = it
                    errorMessage = ""
                },
                label = { Text(stringResource(R.string.security_question_answer)) },
                leadingIcon = {
                    Icon(Icons.Default.QuestionAnswer, contentDescription = null)
                },
                placeholder = { Text(stringResource(R.string.security_question_answer_hint)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                singleLine = true
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            // 问题3
            Text(
                stringResource(R.string.security_question_label_3),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            var expanded3 by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded3,
                onExpandedChange = { expanded3 = it }
            ) {
                OutlinedTextField(
                    value = question3,
                    onValueChange = { 
                        question3 = it
                        errorMessage = ""
                    },
                    label = { Text(stringResource(R.string.security_question_select)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded3)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                )
                
                ExposedDropdownMenu(
                    expanded = expanded3,
                    onDismissRequest = { expanded3 = false }
                ) {
                    presetQuestions.forEach { question ->
                        DropdownMenuItem(
                            text = { Text(question) },
                            onClick = {
                                question3 = question
                                expanded3 = false
                            }
                        )
                    }
                }
            }
            
            OutlinedTextField(
                value = answer3,
                onValueChange = { 
                    answer3 = it
                    errorMessage = ""
                },
                label = { Text(stringResource(R.string.security_question_answer)) },
                leadingIcon = {
                    Icon(Icons.Default.QuestionAnswer, contentDescription = null)
                },
                placeholder = { Text(stringResource(R.string.security_question_answer_hint)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                singleLine = true
            )
            
            // 错误提示
            if (errorMessage.isNotEmpty()) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 保存按钮
            Button(
                onClick = {
                    when {
                        question1.isBlank() || answer1.isBlank() -> {
                            errorMessage = errorQ1Empty
                        }
                        question2.isBlank() || answer2.isBlank() -> {
                            errorMessage = errorQ2Empty
                        }
                        question3.isBlank() || answer3.isBlank() -> {
                            errorMessage = errorQ3Empty
                        }
                        question1 == question2 || question2 == question3 || question1 == question3 -> {
                            errorMessage = errorDuplicate
                        }
                        else -> {
                            val questions = listOf(
                                question1 to answer1,
                                question2 to answer2,
                                question3 to answer3
                            )
                            onSaveQuestions(questions)
                            showSuccessDialog = true
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.security_question_save))
            }
        }
    }
    
    // 成功对话框
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { },
            icon = {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text(stringResource(R.string.security_question_success_title)) },
            text = { Text(stringResource(R.string.security_question_success_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSuccessDialog = false
                        onNavigateBack()
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        )
    }
}
