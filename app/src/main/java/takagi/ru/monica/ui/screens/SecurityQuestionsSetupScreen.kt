package takagi.ru.monica.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.PredefinedSecurityQuestions
import takagi.ru.monica.data.SecurityQuestion
import takagi.ru.monica.security.SecurityManager
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityQuestionsSetupScreen(
    securityManager: SecurityManager,
    onNavigateBack: () -> Unit,
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val isZh = Locale.getDefault().language == "zh"
    val questions = PredefinedSecurityQuestions.getQuestions(isZh)
    
    var selectedQuestion1 by remember { mutableStateOf<SecurityQuestion?>(null) }
    var selectedQuestion2 by remember { mutableStateOf<SecurityQuestion?>(null) }
    var answer1 by remember { mutableStateOf("") }
    var answer2 by remember { mutableStateOf("") }
    var showQuestion1Dropdown by remember { mutableStateOf(false) }
    var showQuestion2Dropdown by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    val isExistingSetup = securityManager.areSecurityQuestionsSet()
    
    // Load existing questions if editing
    LaunchedEffect(Unit) {
        if (isExistingSetup) {
            val question1Id = securityManager.getSecurityQuestion1Id()
            val question2Id = securityManager.getSecurityQuestion2Id()
            selectedQuestion1 = PredefinedSecurityQuestions.getQuestionById(question1Id, isZh)
            selectedQuestion2 = PredefinedSecurityQuestions.getQuestionById(question2Id, isZh)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (isExistingSetup) 
                            context.getString(R.string.edit_security_questions) 
                        else 
                            context.getString(R.string.setup_security_questions)
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = context.getString(R.string.back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Description Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = context.getString(R.string.security_questions_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            // Question 1
            Text(
                text = context.getString(R.string.security_question_1),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            // Question 1 Dropdown
            ExposedDropdownMenuBox(
                expanded = showQuestion1Dropdown,
                onExpandedChange = { showQuestion1Dropdown = !showQuestion1Dropdown }
            ) {
                OutlinedTextField(
                    value = selectedQuestion1?.questionText ?: "",
                    onValueChange = { },
                    readOnly = true,
                    label = { Text(context.getString(R.string.select_question)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showQuestion1Dropdown) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                )
                
                ExposedDropdownMenu(
                    expanded = showQuestion1Dropdown,
                    onDismissRequest = { showQuestion1Dropdown = false }
                ) {
                    questions.forEach { question ->
                        DropdownMenuItem(
                            text = { Text(question.questionText) },
                            onClick = {
                                selectedQuestion1 = question
                                showQuestion1Dropdown = false
                                errorMessage = ""
                            }
                        )
                    }
                }
            }
            
            // Answer 1
            OutlinedTextField(
                value = answer1,
                onValueChange = { 
                    answer1 = it
                    errorMessage = ""
                },
                label = { Text(context.getString(R.string.your_answer)) },
                placeholder = { Text(context.getString(R.string.enter_answer_hint)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Question 2
            Text(
                text = context.getString(R.string.security_question_2),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            // Question 2 Dropdown
            ExposedDropdownMenuBox(
                expanded = showQuestion2Dropdown,
                onExpandedChange = { showQuestion2Dropdown = !showQuestion2Dropdown }
            ) {
                OutlinedTextField(
                    value = selectedQuestion2?.questionText ?: "",
                    onValueChange = { },
                    readOnly = true,
                    label = { Text(context.getString(R.string.select_question)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showQuestion2Dropdown) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                )
                
                ExposedDropdownMenu(
                    expanded = showQuestion2Dropdown,
                    onDismissRequest = { showQuestion2Dropdown = false }
                ) {
                    questions.filter { it.id != selectedQuestion1?.id }.forEach { question ->
                        DropdownMenuItem(
                            text = { Text(question.questionText) },
                            onClick = {
                                selectedQuestion2 = question
                                showQuestion2Dropdown = false
                                errorMessage = ""
                            }
                        )
                    }
                }
            }
            
            // Answer 2
            OutlinedTextField(
                value = answer2,
                onValueChange = { 
                    answer2 = it
                    errorMessage = ""
                },
                label = { Text(context.getString(R.string.your_answer)) },
                placeholder = { Text(context.getString(R.string.enter_answer_hint)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )
            
            // Error Message
            if (errorMessage.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Save Button
            Button(
                onClick = {
                    when {
                        selectedQuestion1 == null -> {
                            errorMessage = context.getString(R.string.select_first_question)
                        }
                        selectedQuestion2 == null -> {
                            errorMessage = context.getString(R.string.select_second_question)
                        }
                        selectedQuestion1?.id == selectedQuestion2?.id -> {
                            errorMessage = context.getString(R.string.questions_must_be_different)
                        }
                        answer1.trim().isEmpty() -> {
                            errorMessage = context.getString(R.string.first_answer_required)
                        }
                        answer2.trim().isEmpty() -> {
                            errorMessage = context.getString(R.string.second_answer_required)
                        }
                        else -> {
                            isLoading = true
                            errorMessage = ""
                            
                            securityManager.setSecurityQuestions(
                                selectedQuestion1!!.id,
                                answer1.trim(),
                                selectedQuestion2!!.id,
                                answer2.trim()
                            )
                            
                            isLoading = false
                            onSetupComplete()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && selectedQuestion1 != null && selectedQuestion2 != null && 
                         answer1.trim().isNotEmpty() && answer2.trim().isNotEmpty()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    if (isExistingSetup) 
                        context.getString(R.string.update_questions) 
                    else 
                        context.getString(R.string.save_questions)
                )
            }
        }
    }
}