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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.PredefinedSecurityQuestions
import takagi.ru.monica.security.SecurityManager
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityQuestionsVerificationScreen(
    securityManager: SecurityManager,
    onNavigateBack: () -> Unit,
    onVerificationSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val isZh = Locale.getDefault().language == "zh"
    
    var answer1 by remember { mutableStateOf("") }
    var answer2 by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var attemptCount by remember { mutableStateOf(0) }
    
    val question1Id = securityManager.getSecurityQuestion1Id()
    val question2Id = securityManager.getSecurityQuestion2Id()
    val question1 = PredefinedSecurityQuestions.getQuestionById(question1Id, isZh)
    val question2 = PredefinedSecurityQuestions.getQuestionById(question2Id, isZh)
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(context.getString(R.string.verify_identity)) },
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
            // Security Icon
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
            }
            
            // Title
            Text(
                text = context.getString(R.string.answer_security_questions),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Description
            Text(
                text = context.getString(R.string.security_questions_verify_description),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Question 1
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = context.getString(R.string.question_1),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = question1?.questionText ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            OutlinedTextField(
                value = answer1,
                onValueChange = { 
                    answer1 = it
                    errorMessage = ""
                },
                label = { Text(context.getString(R.string.your_answer)) },
                placeholder = { Text(context.getString(R.string.enter_answer_hint)) },
                leadingIcon = {
                    Icon(Icons.Default.QuestionAnswer, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                singleLine = true
            )
            
            // Question 2
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = context.getString(R.string.question_2),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = question2?.questionText ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            OutlinedTextField(
                value = answer2,
                onValueChange = { 
                    answer2 = it
                    errorMessage = ""
                },
                label = { Text(context.getString(R.string.your_answer)) },
                placeholder = { Text(context.getString(R.string.enter_answer_hint)) },
                leadingIcon = {
                    Icon(Icons.Default.QuestionAnswer, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                singleLine = true
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
            
            // Attempt counter
            if (attemptCount > 0) {
                Text(
                    text = context.getString(R.string.verification_attempts, attemptCount, 3),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Verify Button
            Button(
                onClick = {
                    when {
                        answer1.trim().isEmpty() -> {
                            errorMessage = context.getString(R.string.first_answer_required)
                        }
                        answer2.trim().isEmpty() -> {
                            errorMessage = context.getString(R.string.second_answer_required)
                        }
                        else -> {
                            isLoading = true
                            errorMessage = ""
                            
                            if (securityManager.verifySecurityAnswers(answer1.trim(), answer2.trim())) {
                                isLoading = false
                                onVerificationSuccess()
                            } else {
                                attemptCount++
                                isLoading = false
                                
                                if (attemptCount >= 3) {
                                    errorMessage = context.getString(R.string.too_many_attempts)
                                    // Could navigate back or show different options
                                } else {
                                    errorMessage = context.getString(R.string.incorrect_answers)
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && answer1.trim().isNotEmpty() && answer2.trim().isNotEmpty() && attemptCount < 3
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(context.getString(R.string.verify_answers))
            }
            
            // Alternative option if too many attempts
            if (attemptCount >= 3) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(context.getString(R.string.back_to_login))
                }
            }
        }
    }
}