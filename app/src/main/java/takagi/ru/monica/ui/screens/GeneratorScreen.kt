package takagi.ru.monica.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Done
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
    
    val passwordLength by viewModel.passwordLength.collectAsState()
    val firstLetterUppercase by viewModel.firstLetterUppercase.collectAsState()
    val includeNumbersInPassword by viewModel.includeNumbersInPassword.collectAsState()
    val customSeparator by viewModel.customSeparator.collectAsState()
    val separatorCountsTowardsLength by viewModel.separatorCountsTowardsLength.collectAsState()
    val segmentLength by viewModel.segmentLength.collectAsState()
    val passwordResult by viewModel.passwordResult.collectAsState()
    
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
            
            // 生成器类型选择按钮 - 书签式按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BookmarkTab(
                    text = stringResource(R.string.random_symbol_generator_tab),
                    isSelected = selectedGenerator == GeneratorType.SYMBOL,
                    onClick = { viewModel.updateSelectedGenerator(GeneratorType.SYMBOL) },
                    modifier = Modifier.weight(1f)
                )
                
                BookmarkTab(
                    text = stringResource(R.string.password_generator_tab),
                    isSelected = selectedGenerator == GeneratorType.PASSWORD,
                    onClick = { viewModel.updateSelectedGenerator(GeneratorType.PASSWORD) },
                    modifier = Modifier.weight(1f)
                )
                
                BookmarkTab(
                    text = stringResource(R.string.pin_generator_tab),
                    isSelected = selectedGenerator == GeneratorType.PIN,
                    onClick = { viewModel.updateSelectedGenerator(GeneratorType.PIN) },
                    modifier = Modifier.weight(1f)
                )
            }
            
            // 结果显示卡片区域 - 独立显示在标签按钮区域和配置选项之间
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                when (selectedGenerator) {
                    GeneratorType.SYMBOL -> {
                        if (symbolResult.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ResultDisplay(
                                    result = symbolResult,
                                    onCopy = { text ->
                                        copyToClipboard(context, text)
                                        Toast.makeText(context, R.string.generator_copied, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                    GeneratorType.PASSWORD -> {
                        if (passwordResult.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ResultDisplay(
                                    result = passwordResult,
                                    onCopy = { text ->
                                        copyToClipboard(context, text)
                                        Toast.makeText(context, R.string.generator_copied, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                    GeneratorType.PIN -> {
                        if (pinResult.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ResultDisplay(
                                    result = pinResult,
                                    onCopy = { text ->
                                        copyToClipboard(context, text)
                                        Toast.makeText(context, R.string.generator_copied, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
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
                            text = stringResource(R.string.random_symbol_generator),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        // 长度滑块
                        Text(
                            text = "${stringResource(R.string.generator_length)}: $symbolLength",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = symbolLength.toFloat(),
                            onValueChange = { viewModel.updateSymbolLength(it.toInt()) },
                            valueRange = 1f..128f,
                            steps = 127,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // 复选框选项
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CheckboxWithText(
                                text = stringResource(R.string.generator_include_uppercase),
                                checked = includeUppercase,
                                onCheckedChange = { viewModel.updateIncludeUppercase(it) }
                            )
                            
                            CheckboxWithText(
                                text = stringResource(R.string.generator_include_lowercase),
                                checked = includeLowercase,
                                onCheckedChange = { viewModel.updateIncludeLowercase(it) }
                            )
                            
                            CheckboxWithText(
                                text = stringResource(R.string.generator_include_numbers),
                                checked = includeNumbers,
                                onCheckedChange = { viewModel.updateIncludeNumbers(it) }
                            )
                            
                            CheckboxWithText(
                                text = stringResource(R.string.generator_include_symbols),
                                checked = includeSymbols,
                                onCheckedChange = { viewModel.updateIncludeSymbols(it) }
                            )
                            
                            CheckboxWithText(
                                text = stringResource(R.string.generator_exclude_similar),
                                checked = excludeSimilar,
                                onCheckedChange = { viewModel.updateExcludeSimilar(it) }
                            )
                            
                            CheckboxWithText(
                                text = stringResource(R.string.generator_exclude_ambiguous),
                                checked = excludeAmbiguous,
                                onCheckedChange = { viewModel.updateExcludeAmbiguous(it) }
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
                
                GeneratorType.PIN -> {
                    // PIN码生成器配置选项
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.pin_generator),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        // 长度滑块
                        Text(
                            text = "${stringResource(R.string.generator_pin_length)}: $pinLength",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = pinLength.toFloat(),
                            onValueChange = { viewModel.updatePinLength(it.toInt()) },
                            valueRange = 1f..32f,
                            steps = 31,
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
                            excludeAmbiguous = excludeAmbiguous
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
                    GeneratorType.PIN -> {
                        val result = generatePin(pinLength)
                        viewModel.updatePinResult(result)
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            text = {
                Text(
                    text = stringResource(R.string.generator_action),
                    style = MaterialTheme.typography.labelLarge
                )
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Done,
                    contentDescription = stringResource(R.string.generator_generate)
                )
            }
        )
    }
}

// 生成器类型枚举
enum class GeneratorType {
    SYMBOL, PASSWORD, PIN
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