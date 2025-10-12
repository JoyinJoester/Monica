package takagi.ru.monica.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import takagi.ru.monica.R
import takagi.ru.monica.util.PasswordGenerator
import takagi.ru.monica.viewmodel.GeneratorViewModel
import takagi.ru.monica.viewmodel.GeneratorType
import kotlin.random.Random
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorScreen(
    onNavigateBack: () -> Unit,
    viewModel: GeneratorViewModel = viewModel()
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    // 从ViewModel收集状态
    val selectedGenerator by viewModel.selectedGenerator.collectAsState()
    val symbolLength by viewModel.symbolLength.collectAsState()
    val includeUppercase by viewModel.includeUppercase.collectAsState()
    val includeLowercase by viewModel.includeLowercase.collectAsState()
    val includeNumbers by viewModel.includeNumbers.collectAsState()
    val includeSymbols by viewModel.includeSymbols.collectAsState()
    val excludeSimilar by viewModel.excludeSimilar.collectAsState()
    val excludeAmbiguous by viewModel.excludeAmbiguous.collectAsState()
    val symbolResult by viewModel.symbolResult.collectAsState()
    
    // 最小字符数要求状态
    val uppercaseMin by viewModel.uppercaseMin.collectAsState()
    val lowercaseMin by viewModel.lowercaseMin.collectAsState()
    val numbersMin by viewModel.numbersMin.collectAsState()
    val symbolsMin by viewModel.symbolsMin.collectAsState()
    
    val passwordLength by viewModel.passwordLength.collectAsState()
    val firstLetterUppercase by viewModel.firstLetterUppercase.collectAsState()
    val includeNumbersInPassword by viewModel.includeNumbersInPassword.collectAsState()
    val customSeparator by viewModel.customSeparator.collectAsState()
    val separatorCountsTowardsLength by viewModel.separatorCountsTowardsLength.collectAsState()
    val segmentLength by viewModel.segmentLength.collectAsState()
    val passwordResult by viewModel.passwordResult.collectAsState()
    
    // 密码短语状态
    val passphraseWordCount by viewModel.passphraseWordCount.collectAsState()
    val passphraseDelimiter by viewModel.passphraseDelimiter.collectAsState()
    val passphraseCapitalize by viewModel.passphraseCapitalize.collectAsState()
    val passphraseIncludeNumber by viewModel.passphraseIncludeNumber.collectAsState()
    val passphraseCustomWord by viewModel.passphraseCustomWord.collectAsState()
    val passphraseResult by viewModel.passphraseResult.collectAsState()
    
    val pinLength by viewModel.pinLength.collectAsState()
    val pinResult by viewModel.pinResult.collectAsState()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // 页面标题
            Text(
                text = stringResource(R.string.generator_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )
            
            // 生成器类型选择按钮 - Pill 样式按钮
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    FilterChipTab(
                        text = "符号密码",
                        isSelected = selectedGenerator == GeneratorType.SYMBOL,
                        onClick = { viewModel.updateSelectedGenerator(GeneratorType.SYMBOL) },
                        modifier = Modifier.weight(1f)
                    )
                    
                    FilterChipTab(
                        text = "单词密码",
                        isSelected = selectedGenerator == GeneratorType.PASSWORD,
                        onClick = { viewModel.updateSelectedGenerator(GeneratorType.PASSWORD) },
                        modifier = Modifier.weight(1f)
                    )
                    
                    FilterChipTab(
                        text = "密码短语",
                        isSelected = selectedGenerator == GeneratorType.PASSPHRASE,
                        onClick = { viewModel.updateSelectedGenerator(GeneratorType.PASSPHRASE) },
                        modifier = Modifier.weight(1f)
                    )
                    
                    FilterChipTab(
                        text = "PIN码",
                        isSelected = selectedGenerator == GeneratorType.PIN,
                        onClick = { viewModel.updateSelectedGenerator(GeneratorType.PIN) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            // 结果显示卡片区域 - 优化后的显示效果
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
            ) {
                when (selectedGenerator) {
                    GeneratorType.SYMBOL -> {
                        if (symbolResult.isNotEmpty()) {
                            ResultCard(
                                result = symbolResult,
                                onCopy = { text ->
                                    copyToClipboard(context, text)
                                    Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                    GeneratorType.PASSWORD -> {
                        if (passwordResult.isNotEmpty()) {
                            ResultCard(
                                result = passwordResult,
                                onCopy = { text ->
                                    copyToClipboard(context, text)
                                    Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                    GeneratorType.PASSPHRASE -> {
                        if (passphraseResult.isNotEmpty()) {
                            ResultCard(
                                result = passphraseResult,
                                onCopy = { text ->
                                    copyToClipboard(context, text)
                                    Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                    GeneratorType.PIN -> {
                        if (pinResult.isNotEmpty()) {
                            ResultCard(
                                result = pinResult,
                                onCopy = { text ->
                                    copyToClipboard(context, text)
                                    Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
            
            // 根据选择的生成器类型显示相应的配置选项
            when (selectedGenerator) {
                GeneratorType.SYMBOL -> {
                    // 随机符号生成器配置选项
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = "符号密码生成器",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        // 长度滑块
                        Text(
                            text = "长度: $symbolLength",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = symbolLength.toFloat(),
                            onValueChange = { viewModel.updateSymbolLength(it.toInt()) },
                            valueRange = 1f..128f,
                            steps = 127,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // 字符类型复选框选项
                        Text(
                            text = "字符类型",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CheckboxWithText(
                                text = "包含大写字母 (A-Z)",
                                checked = includeUppercase,
                                onCheckedChange = { viewModel.updateIncludeUppercase(it) }
                            )
                            
                            CheckboxWithText(
                                text = "包含小写字母 (a-z)",
                                checked = includeLowercase,
                                onCheckedChange = { viewModel.updateIncludeLowercase(it) }
                            )
                            
                            CheckboxWithText(
                                text = "包含数字 (0-9)",
                                checked = includeNumbers,
                                onCheckedChange = { viewModel.updateIncludeNumbers(it) }
                            )
                            
                            CheckboxWithText(
                                text = "包含符号 (!@#$%...)",
                                checked = includeSymbols,
                                onCheckedChange = { viewModel.updateIncludeSymbols(it) }
                            )
                            
                            CheckboxWithText(
                                text = "排除相似字符 (0, O, l, 1)",
                                checked = excludeSimilar,
                                onCheckedChange = { viewModel.updateExcludeSimilar(it) }
                            )
                            
                            CheckboxWithText(
                                text = "排除容易混淆的字符",
                                checked = excludeAmbiguous,
                                onCheckedChange = { viewModel.updateExcludeAmbiguous(it) }
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // 最小字符数要求
                        Text(
                            text = "最小字符数要求",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Column {
                            // 大写字母最小数量
                            Text(
                                text = "最少大写字母: $uppercaseMin",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = uppercaseMin.toFloat(),
                                onValueChange = { viewModel.updateUppercaseMin(it.toInt()) },
                                valueRange = 0f..10f,
                                steps = 10,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = includeUppercase
                            )
                            
                            // 小写字母最小数量
                            Text(
                                text = "最少小写字母: $lowercaseMin",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = lowercaseMin.toFloat(),
                                onValueChange = { viewModel.updateLowercaseMin(it.toInt()) },
                                valueRange = 0f..10f,
                                steps = 10,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = includeLowercase
                            )
                            
                            // 数字最小数量
                            Text(
                                text = "最少数字: $numbersMin",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = numbersMin.toFloat(),
                                onValueChange = { viewModel.updateNumbersMin(it.toInt()) },
                                valueRange = 0f..10f,
                                steps = 10,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = includeNumbers
                            )
                            
                            // 符号最小数量
                            Text(
                                text = "最少符号: $symbolsMin",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = symbolsMin.toFloat(),
                                onValueChange = { viewModel.updateSymbolsMin(it.toInt()) },
                                valueRange = 0f..10f,
                                steps = 10,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = includeSymbols
                            )
                        }
                    }
                }
                
                GeneratorType.PASSWORD -> {
                    // 口令生成器配置选项
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.password_generator),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        // 长度滑块
                        Text(
                            text = "${stringResource(R.string.generator_length)}: $passwordLength",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = passwordLength.toFloat(),
                            onValueChange = { viewModel.updatePasswordLength(it.toInt()) },
                            valueRange = 4f..128f,
                            steps = 124,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // 复选框选项
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CheckboxWithText(
                                text = stringResource(R.string.generator_first_letter_uppercase),
                                checked = firstLetterUppercase,
                                onCheckedChange = { viewModel.updateFirstLetterUppercase(it) }
                            )
                            
                            CheckboxWithText(
                                text = stringResource(R.string.generator_include_numbers_in_password),
                                checked = includeNumbersInPassword,
                                onCheckedChange = { viewModel.updateIncludeNumbersInPassword(it) }
                            )
                            
                            // 新增：分隔符是否计入长度
                            CheckboxWithText(
                                text = stringResource(R.string.generator_separator_counts_towards_length),
                                checked = separatorCountsTowardsLength,
                                onCheckedChange = { viewModel.updateSeparatorCountsTowardsLength(it) }
                            )
                        }
                        
                        // 自定义分隔符
                        OutlinedTextField(
                            value = customSeparator,
                            onValueChange = { viewModel.updateCustomSeparator(it) },
                            label = { Text(stringResource(R.string.generator_custom_separator)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )
                        
                        // 修改：分段长度滑动条
                        Text(
                            text = "${stringResource(R.string.generator_segment_length)}: $segmentLength",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Slider(
                            value = segmentLength.toFloat(),
                            onValueChange = { viewModel.updateSegmentLength(it.toInt()) },
                            valueRange = 0f..128f,  // 0表示不使用分段功能
                            steps = 128,  // 步进值为1
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                GeneratorType.PASSPHRASE -> {
                    // 密码短语生成器配置选项
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = "密码短语生成器",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        // 单词数量滑块
                        Text(
                            text = "单词数量: $passphraseWordCount",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = passphraseWordCount.toFloat(),
                            onValueChange = { viewModel.updatePassphraseWordCount(it.toInt()) },
                            valueRange = 3f..8f,
                            steps = 5,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // 分隔符输入
                        OutlinedTextField(
                            value = passphraseDelimiter,
                            onValueChange = { viewModel.updatePassphraseDelimiter(it) },
                            label = { Text("分隔符") },
                            placeholder = { Text("例如: - 或 _ 或 . 或空格") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // 选项设置
                        CheckboxWithText(
                            text = "首字母大写",
                            checked = passphraseCapitalize,
                            onCheckedChange = { viewModel.updatePassphraseCapitalize(it) }
                        )
                        
                        CheckboxWithText(
                            text = "在末尾添加数字",
                            checked = passphraseIncludeNumber,
                            onCheckedChange = { viewModel.updatePassphraseIncludeNumber(it) }
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // 自定义单词
                        OutlinedTextField(
                            value = passphraseCustomWord,
                            onValueChange = { viewModel.updatePassphraseCustomWord(it) },
                            label = { Text("自定义单词（可选）") },
                            placeholder = { Text("将包含在短语中的自定义单词") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
                
                GeneratorType.PIN -> {
                    // PIN码生成器配置选项
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = "PIN码生成器",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        // 长度滑块
                        Text(
                            text = "PIN码长度: $pinLength",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = pinLength.toFloat(),
                            onValueChange = { viewModel.updatePinLength(it.toInt()) },
                            valueRange = 3f..9f,
                            steps = 6,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        
        // 生成按钮 - 固定在右下角的悬浮位置（长方形ExtendedFloatingActionButton）
        ExtendedFloatingActionButton(
            onClick = {
                when (selectedGenerator) {
                    GeneratorType.SYMBOL -> {
                        val result = PasswordGenerator.generatePassword(
                            length = symbolLength,
                            includeUppercase = includeUppercase,
                            includeLowercase = includeLowercase,
                            includeNumbers = includeNumbers,
                            includeSymbols = includeSymbols,
                            excludeSimilar = excludeSimilar,
                            excludeAmbiguous = excludeAmbiguous,
                            uppercaseMin = uppercaseMin,
                            lowercaseMin = lowercaseMin,
                            numbersMin = numbersMin,
                            symbolsMin = symbolsMin
                        )
                        viewModel.updateSymbolResult(result)
                    }
                    GeneratorType.PASSWORD -> {
                        val result = generatePassword(
                            length = passwordLength,
                            firstLetterUppercase = firstLetterUppercase,
                            includeNumbers = includeNumbersInPassword,
                            separator = customSeparator,
                            separatorCountsTowardsLength = separatorCountsTowardsLength,
                            segmentLength = segmentLength
                        )
                        viewModel.updatePasswordResult(result)
                    }
                    GeneratorType.PASSPHRASE -> {
                        val result = PasswordGenerator.generatePassphrase(
                            context = context,
                            wordCount = passphraseWordCount,
                            delimiter = passphraseDelimiter,
                            capitalize = passphraseCapitalize,
                            includeNumber = passphraseIncludeNumber,
                            customWord = passphraseCustomWord.takeIf { it.isNotEmpty() }
                        )
                        viewModel.updatePassphraseResult(result)
                    }
                    GeneratorType.PIN -> {
                        val result = PasswordGenerator.generatePinCode(pinLength)
                        viewModel.updatePinResult(result)
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            text = {
                Text(
                    text = "生成",
                    style = MaterialTheme.typography.labelLarge
                )
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Done,
                    contentDescription = "生成密码"
                )
            }
        )
    }
}



@Composable
fun BookmarkTab(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(48.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = 8.dp,
            topEnd = 8.dp,
            bottomStart = 0.dp,
            bottomEnd = 0.dp
        ),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        onClick = onClick
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
fun CheckboxWithText(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
fun ResultDisplay(
    result: String,
    onCopy: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.generator_result),
            style = MaterialTheme.typography.bodyMedium
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(1f)
            ) {
                ColorCodedText(
                    text = result,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            
            IconButton(
                onClick = { onCopy(result) }
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.generator_copy)
                )
            }
        }
    }
}

@Composable
fun ColorCodedText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    // 创建带颜色的AnnotatedString
    val annotatedString = buildAnnotatedString {
        text.forEach { char ->
            when {
                char.isDigit() -> {
                    // 数字字符显示为蓝色
                    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                        append(char)
                    }
                }
                char.isLetter() -> {
                    // 字母字符显示为白色（或根据主题的onSurface颜色）
                    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                        append(char)
                    }
                }
                else -> {
                    // 符号字符显示为红色（或使用secondary颜色以确保可读性）
                    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.secondary)) {
                        append(char)
                    }
                }
            }
        }
    }
    
    Text(
        text = annotatedString,
        style = style,
        modifier = modifier
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Generated Text", text)
    clipboard.setPrimaryClip(clip)
}

private fun generatePassword(
    length: Int,
    firstLetterUppercase: Boolean,
    includeNumbers: Boolean,
    separator: String,
    separatorCountsTowardsLength: Boolean = false,
    segmentLength: Int = 0
): String {
    val words = listOf(
        "apple", "banana", "cherry", "date", "elderberry",
        "fig", "grape", "honeydew", "kiwi", "lemon",
        "mango", "nectarine", "orange", "papaya", "quince",
        "raspberry", "strawberry", "tangerine", "watermelon", "blueberry"
    )
    
    // 确保分段长度有效（大于0且小于总长度）
    val effectiveSegmentLength = if (segmentLength > 0 && segmentLength < length) segmentLength else 0
    
    // 如果没有有效的分段长度或分隔符为空，则按原来的方式生成密码
    if (effectiveSegmentLength == 0 || separator.isEmpty()) {
        // 构建基础密码
        val targetWordCount = (length / 6).coerceIn(2, 5) // 至少2个单词，最多5个
        val selectedWords = (1..targetWordCount).map { words.random() }.toMutableList()
        
        // 处理首字母大写
        if (firstLetterUppercase) {
            selectedWords[0] = selectedWords[0].replaceFirstChar { it.uppercase() }
        }
        
        var result = selectedWords.joinToString("")
        
        // 添加数字
        val numbers = if (includeNumbers) {
            val remainingLength = (length - result.length).coerceAtLeast(0)
            if (remainingLength > 0) {
                (1..remainingLength).joinToString("") { Random.nextInt(0, 10).toString() }
            } else {
                ""
            }
        } else {
            ""
        }
        
        result += numbers
        return result.take(length)
    }
    
    // 处理分隔符的情况
    val separatorChar = separator.first()
    
    if (separatorCountsTowardsLength) {
        // 分隔符计入长度：需要在生成密码时就考虑分隔符占用的空间
        
        // 计算在指定长度内可以有多少个分隔符
        val maxSeparators = (length - 1) / effectiveSegmentLength
        val contentLength = length - maxSeparators
        
        // 生成指定长度的内容
        val targetWordCount = (contentLength / 6).coerceIn(2, 5)
        val selectedWords = (1..targetWordCount).map { words.random() }.toMutableList()
        
        // 处理首字母大写
        if (firstLetterUppercase) {
            selectedWords[0] = selectedWords[0].replaceFirstChar { it.uppercase() }
        }
        
        var content = selectedWords.joinToString("")
        
        // 添加数字
        val numbers = if (includeNumbers) {
            val remainingLength = (contentLength - content.length).coerceAtLeast(0)
            if (remainingLength > 0) {
                (1..remainingLength).joinToString("") { Random.nextInt(0, 10).toString() }
            } else {
                ""
            }
        } else {
            ""
        }
        
        content += numbers
        content = content.take(contentLength)
        
        // 插入分隔符
        val builder = StringBuilder()
        for (i in content.indices) {
            builder.append(content[i])
            // 在指定位置添加分隔符，但不包括最后一个字符
            if ((i + 1) % effectiveSegmentLength == 0 && i < content.length - 1 && builder.length < length) {
                builder.append(separatorChar)
            }
        }
        
        return builder.toString().take(length)
    } else {
        // 分隔符不计入长度：先生成指定长度的内容，然后再插入分隔符
        
        // 生成指定长度的内容
        val targetWordCount = (length / 6).coerceIn(2, 5)
        val selectedWords = (1..targetWordCount).map { words.random() }.toMutableList()
        
        // 处理首字母大写
        if (firstLetterUppercase) {
            selectedWords[0] = selectedWords[0].replaceFirstChar { it.uppercase() }
        }
        
        var content = selectedWords.joinToString("")
        
        // 添加数字
        val numbers = if (includeNumbers) {
            val remainingLength = (length - content.length).coerceAtLeast(0)
            if (remainingLength > 0) {
                (1..remainingLength).joinToString("") { Random.nextInt(0, 10).toString() }
            } else {
                ""
            }
        } else {
            ""
        }
        
        content += numbers
        content = content.take(length)
        
        // 插入分隔符
        val builder = StringBuilder()
        for (i in content.indices) {
            builder.append(content[i])
            // 在指定位置添加分隔符，但不包括最后一个字符
            if ((i + 1) % effectiveSegmentLength == 0 && i < content.length - 1) {
                builder.append(separatorChar)
            }
        }
        
        return builder.toString()
    }
}

private fun generatePin(length: Int): String {
    val digits = "0123456789"
    return (1..length)
        .map { digits.random() }
        .joinToString("")
}

/**
 * Pill 样式的标签按钮
 */
@Composable
private fun FilterChipTab(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) 
            MaterialTheme.colorScheme.primary 
        else 
            Color.Transparent,
        tonalElevation = if (isSelected) 2.dp else 0.dp
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) 
                    MaterialTheme.colorScheme.onPrimary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/**
 * 优化后的结果显示卡片
 */
@Composable
private fun ResultCard(
    result: String,
    onCopy: (String) -> Unit
) {
    var showCopied by remember { mutableStateOf(false) }
    
    LaunchedEffect(showCopied) {
        if (showCopied) {
            kotlinx.coroutines.delay(1500)
            showCopied = false
        }
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 4.dp
        ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                
                FilledTonalButton(
                    onClick = {
                        onCopy(result)
                        showCopied = true
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (showCopied) 
                            Color(0xFF4CAF50) 
                        else 
                            MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(
                        imageVector = if (showCopied) 
                            Icons.Default.Done 
                        else 
                            Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (showCopied) "已复制" else "复制",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 结果文本 - 使用等宽字体
            SelectionContainer {
                Text(
                    text = result,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    ),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    lineHeight = MaterialTheme.typography.headlineSmall.lineHeight * 1.3f
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 长度信息
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "长度: ${result.length} 字符",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}