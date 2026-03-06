package takagi.ru.monica.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import takagi.ru.monica.R
import takagi.ru.monica.util.PasswordGenerator
import takagi.ru.monica.viewmodel.GeneratorViewModel
import takagi.ru.monica.viewmodel.GeneratorType
import takagi.ru.monica.viewmodel.PasswordViewModel
import takagi.ru.monica.data.PasswordHistoryManager
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.PasswordGenerationHistory
import takagi.ru.monica.data.HistoryFilterPreferences
import takagi.ru.monica.data.HistoryFilterSettings
import java.util.Date
import kotlin.random.Random
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState

/**
 * 将密码转换为彩色文本
 * 数字:蓝色，符号:红色，字母:主题自适应颜色
 */
@Composable
fun colorizePassword(password: String): AnnotatedString {
    val colorScheme = MaterialTheme.colorScheme
    return buildAnnotatedString {
        password.forEach { char ->
            val color = when {
                char.isDigit() -> colorScheme.secondary
                char.isLetter() -> colorScheme.onSurface
                else -> colorScheme.error
            }
            withStyle(style = SpanStyle(color = color)) {
                append(char)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun GeneratorScreen(
    onNavigateBack: () -> Unit,
    passwordViewModel: PasswordViewModel,
    externalRefreshRequestKey: Int = 0,
    onRefreshRequestConsumed: () -> Unit = {},
    useExternalRefreshFab: Boolean = false,
    viewModel: GeneratorViewModel = viewModel()
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    // 历史记录状态
    var showHistorySheet by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    val historyManager = remember { PasswordHistoryManager(context) }
    val historyList by historyManager.historyFlow.collectAsState(initial = emptyList())
    
    // 过滤设置
    val filterPreferences = remember { HistoryFilterPreferences(context) }
    val filterSettings by filterPreferences.filterSettings.collectAsState(initial = HistoryFilterSettings())
    
    // 根据过滤设置过滤历史记录
    val filteredHistoryList = remember(historyList, filterSettings) {
        historyList.filter { filterSettings.shouldShow(it.type) }
    }
    
    // 从ViewModel收集状态
    val selectedGenerator by viewModel.selectedGenerator.collectAsState()
    val symbolLength by viewModel.symbolLength.collectAsState()
    val includeUppercase by viewModel.includeUppercase.collectAsState()
    val includeLowercase by viewModel.includeLowercase.collectAsState()
    val includeNumbers by viewModel.includeNumbers.collectAsState()
    val includeSymbols by viewModel.includeSymbols.collectAsState()
    val useSymbolExclusionMode by viewModel.useSymbolExclusionMode.collectAsState()
    val excludedSymbols by viewModel.excludedSymbols.collectAsState()
    val customSymbols by viewModel.customSymbols.collectAsState()
    val excludeSimilar by viewModel.excludeSimilar.collectAsState()
    val excludeAmbiguous by viewModel.excludeAmbiguous.collectAsState()
    val analyzeCommonPasswords by viewModel.analyzeCommonPasswords.collectAsState()
    val analyzeWeight by viewModel.analyzeWeight.collectAsState()
    val symbolResult by viewModel.symbolResult.collectAsState()
    val defaultSymbols = remember { PasswordGenerator.getDefaultSymbols() }
    val allowedSymbols = remember(useSymbolExclusionMode, excludedSymbols, customSymbols, defaultSymbols) {
        if (useSymbolExclusionMode) {
            defaultSymbols.filter { it !in excludedSymbols }
        } else {
            customSymbols
        }
    }
    val filteredAllowedSymbols = remember(allowedSymbols, excludeSimilar, excludeAmbiguous) {
        applyGeneratorSymbolFilters(allowedSymbols, excludeSimilar, excludeAmbiguous)
    }
    val excludedSymbolsSet = remember(excludedSymbols) { excludedSymbols.toSet() }

    // 用户密码数据用于分析
    val passwordEntries by passwordViewModel.passwordEntries.collectAsState()
    val appendGeneratorHistory: (password: String, domain: String, type: String) -> Unit = history@{ password, domain, type ->
        if (password.isBlank()) return@history
        val latest = filteredHistoryList.firstOrNull()
        if (latest?.password == password && latest.type == type) return@history
        scope.launch {
            historyManager.addHistory(
                password = password,
                packageName = "generator",
                domain = domain,
                type = type
            )
        }
    }
    
    // 初始化时生成默认密码
    LaunchedEffect(Unit) {
        if (symbolResult.isEmpty()) {
            val result = PasswordGenerator.generatePassword(
                length = symbolLength,
                includeUppercase = includeUppercase,
                includeLowercase = includeLowercase,
                includeNumbers = includeNumbers,
                includeSymbols = includeSymbols,
                allowedSymbols = allowedSymbols,
                excludeSimilar = excludeSimilar,
                excludeAmbiguous = excludeAmbiguous,
                uppercaseMin = 0,
                lowercaseMin = 0,
                numbersMin = 0,
                symbolsMin = 0
            )
            viewModel.updateSymbolResult(result)
        }
    }
    
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
    val passphraseCustomWords by viewModel.passphraseCustomWords.collectAsState()
    val passphraseResult by viewModel.passphraseResult.collectAsState()
    val parsedCustomMnemonicWords = remember(passphraseCustomWords) {
        passphraseCustomWords
            .split(",", " ", "\n", "\t")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }
    
    val pinLength by viewModel.pinLength.collectAsState()
    val pinResult by viewModel.pinResult.collectAsState()
    var symbolRulesExpanded by remember { mutableStateOf(false) }

    val regenerateNow: () -> Unit = regenerate@{
        when (selectedGenerator) {
            GeneratorType.SYMBOL -> {
                if (includeSymbols && filteredAllowedSymbols.isEmpty()) {
                    viewModel.updateSymbolResult("")
                    return@regenerate
                }
                val result = if (analyzeCommonPasswords && passwordEntries.isNotEmpty()) {
                    PasswordGenerator.generateSimilarPassword(
                        passwords = passwordEntries,
                        targetLength = symbolLength,
                        includeUppercase = includeUppercase,
                        includeLowercase = includeLowercase,
                        includeNumbers = includeNumbers,
                        includeSymbols = includeSymbols,
                        allowedSymbols = allowedSymbols,
                        excludeSimilar = excludeSimilar,
                        excludeAmbiguous = excludeAmbiguous,
                        weightPercent = analyzeWeight
                    )
                } else {
                    PasswordGenerator.generatePassword(
                        length = symbolLength,
                        includeUppercase = includeUppercase,
                        includeLowercase = includeLowercase,
                        includeNumbers = includeNumbers,
                        includeSymbols = includeSymbols,
                        allowedSymbols = allowedSymbols,
                        excludeSimilar = excludeSimilar,
                        excludeAmbiguous = excludeAmbiguous,
                        uppercaseMin = uppercaseMin,
                        lowercaseMin = lowercaseMin,
                        numbersMin = numbersMin,
                        symbolsMin = symbolsMin
                    )
                }
                viewModel.updateSymbolResult(result)
                if (result.isEmpty()) return@regenerate
                appendGeneratorHistory(result, context.getString(R.string.random_symbol_generator), "SYMBOL")
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
                appendGeneratorHistory(result, context.getString(R.string.password_generator), "PASSWORD")
            }
            GeneratorType.PASSPHRASE -> {
                val result = PasswordGenerator.generatePassphrase(
                    context = context,
                    wordCount = passphraseWordCount,
                    delimiter = passphraseDelimiter,
                    capitalize = passphraseCapitalize,
                    includeNumber = passphraseIncludeNumber,
                    customWord = passphraseCustomWord.takeIf { it.isNotEmpty() },
                    customWords = parsedCustomMnemonicWords
                )
                viewModel.updatePassphraseResult(result)
                appendGeneratorHistory(result, context.getString(R.string.passphrase_generator), "PASSPHRASE")
            }
            GeneratorType.PIN -> {
                val result = PasswordGenerator.generatePinCode(pinLength)
                viewModel.updatePinResult(result)
                appendGeneratorHistory(result, context.getString(R.string.pin_generator), "PIN")
            }
        }
    }

    LaunchedEffect(externalRefreshRequestKey) {
        if (externalRefreshRequestKey != 0) {
            regenerateNow()
            onRefreshRequestConsumed()
        }
    }
    
    // ✨ 自动生成：监听参数变化并实时生成
    LaunchedEffect(
        selectedGenerator,
        symbolLength, includeUppercase, includeLowercase, includeNumbers, 
        includeSymbols, useSymbolExclusionMode, excludedSymbols, customSymbols, excludeSimilar, excludeAmbiguous, analyzeCommonPasswords, analyzeWeight,
        uppercaseMin, lowercaseMin, numbersMin, symbolsMin,
        passwordLength, firstLetterUppercase, includeNumbersInPassword, 
        customSeparator, separatorCountsTowardsLength, segmentLength,
        passphraseWordCount, passphraseDelimiter, passphraseCapitalize, 
        passphraseIncludeNumber, passphraseCustomWord, passphraseCustomWords,
        pinLength
    ) {
        // 延迟100ms以避免频繁生成
        kotlinx.coroutines.delay(100)
        
        when (selectedGenerator) {
            GeneratorType.SYMBOL -> {
                if (includeSymbols && filteredAllowedSymbols.isEmpty()) {
                    viewModel.updateSymbolResult("")
                    return@LaunchedEffect
                }
                val result = if (analyzeCommonPasswords && passwordEntries.isNotEmpty()) {
                    PasswordGenerator.generateSimilarPassword(
                        passwords = passwordEntries,
                        targetLength = symbolLength,
                        includeUppercase = includeUppercase,
                        includeLowercase = includeLowercase,
                        includeNumbers = includeNumbers,
                        includeSymbols = includeSymbols,
                        allowedSymbols = allowedSymbols,
                        excludeSimilar = excludeSimilar,
                        excludeAmbiguous = excludeAmbiguous,
                        weightPercent = analyzeWeight
                    )
                } else {
                    PasswordGenerator.generatePassword(
                        length = symbolLength,
                        includeUppercase = includeUppercase,
                        includeLowercase = includeLowercase,
                        includeNumbers = includeNumbers,
                        includeSymbols = includeSymbols,
                        allowedSymbols = allowedSymbols,
                        excludeSimilar = excludeSimilar,
                        excludeAmbiguous = excludeAmbiguous,
                        uppercaseMin = uppercaseMin,
                        lowercaseMin = lowercaseMin,
                        numbersMin = numbersMin,
                        symbolsMin = symbolsMin
                    )
                }
                viewModel.updateSymbolResult(result)
                if (result.isEmpty()) return@LaunchedEffect
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
                    customWord = passphraseCustomWord.takeIf { it.isNotEmpty() },
                    customWords = parsedCustomMnemonicWords
                )
                viewModel.updatePassphraseResult(result)
            }
            GeneratorType.PIN -> {
                val result = PasswordGenerator.generatePinCode(pinLength)
                viewModel.updatePinResult(result)
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .imePadding()
    ) {
        val currentResult = when (selectedGenerator) {
            GeneratorType.SYMBOL -> symbolResult
            GeneratorType.PASSWORD -> passwordResult
            GeneratorType.PASSPHRASE -> passphraseResult
            GeneratorType.PIN -> pinResult
        }
        val isResultCardPinned by remember(listState, currentResult) {
            derivedStateOf {
                currentResult.isNotEmpty() &&
                    (listState.firstVisibleItemIndex > 2 ||
                        (listState.firstVisibleItemIndex == 2 && listState.firstVisibleItemScrollOffset > 0))
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item {
                // 页面标题和历史按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.generator_title),
                        style = MaterialTheme.typography.headlineMedium
                    )

                    IconButton(
                        onClick = { showHistorySheet = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = stringResource(R.string.history),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            item {
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
                            text = stringResource(R.string.generator_symbol),
                            isSelected = selectedGenerator == GeneratorType.SYMBOL,
                            onClick = { viewModel.updateSelectedGenerator(GeneratorType.SYMBOL) },
                            modifier = Modifier.weight(1f)
                        )

                        FilterChipTab(
                            text = stringResource(R.string.generator_word),
                            isSelected = selectedGenerator == GeneratorType.PASSWORD,
                            onClick = { viewModel.updateSelectedGenerator(GeneratorType.PASSWORD) },
                            modifier = Modifier.weight(1f)
                        )

                        FilterChipTab(
                            text = stringResource(R.string.generator_passphrase),
                            isSelected = selectedGenerator == GeneratorType.PASSPHRASE,
                            onClick = { viewModel.updateSelectedGenerator(GeneratorType.PASSPHRASE) },
                            modifier = Modifier.weight(1f)
                        )

                        FilterChipTab(
                            text = stringResource(R.string.generator_pin),
                            isSelected = selectedGenerator == GeneratorType.PIN,
                            onClick = { viewModel.updateSelectedGenerator(GeneratorType.PIN) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (currentResult.isNotEmpty()) {
                stickyHeader {
                    Surface(
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.98f)
                    ) {
                        ResultCard(
                            result = currentResult,
                            compactMode = isResultCardPinned,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = if (isResultCardPinned) 8.dp else 20.dp),
                            onCopy = { text ->
                                copyToClipboard(context, text)
                                Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            item {
                // 根据选择的生成器类型显示相应的配置选项
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        when (selectedGenerator) {
                    GeneratorType.SYMBOL -> {
                    // 随机符号生成器配置选项
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.symbol_password_generator),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        // 长度滑块
                        Text(
                            text = stringResource(R.string.length_label, symbolLength),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = symbolLength.toFloat(),
                            onValueChange = { viewModel.updateSymbolLength(it.toInt()) },
                            valueRange = 1f..30f,
                            steps = 29,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // 字符类型复选框选项
                        Text(
                            text = stringResource(R.string.character_types),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CheckboxWithText(
                                text = stringResource(R.string.include_uppercase),
                                checked = includeUppercase,
                                onCheckedChange = { viewModel.updateIncludeUppercase(it) }
                            )
                            
                            CheckboxWithText(
                                text = stringResource(R.string.include_lowercase),
                                checked = includeLowercase,
                                onCheckedChange = { viewModel.updateIncludeLowercase(it) }
                            )
                            
                            CheckboxWithText(
                                text = stringResource(R.string.include_numbers),
                                checked = includeNumbers,
                                onCheckedChange = { viewModel.updateIncludeNumbers(it) }
                            )
                            
                            CheckboxWithText(
                                text = stringResource(R.string.include_symbols),
                                checked = includeSymbols,
                                onCheckedChange = {
                                    viewModel.updateIncludeSymbols(it)
                                    if (!it && symbolsMin > 0) {
                                        viewModel.updateSymbolsMin(0)
                                    }
                                }
                            )
                            ElevatedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(R.string.generator_symbols_setup),
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                text = stringResource(
                                                    R.string.generator_effective_symbols,
                                                    filteredAllowedSymbols.ifEmpty { "-" }
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (includeSymbols && filteredAllowedSymbols.isEmpty()) {
                                                    MaterialTheme.colorScheme.error
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                        IconButton(
                                            onClick = { symbolRulesExpanded = !symbolRulesExpanded },
                                            enabled = includeSymbols
                                        ) {
                                            Icon(
                                                imageVector = if (symbolRulesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = if (symbolRulesExpanded) "Collapse symbol rules" else "Expand symbol rules"
                                            )
                                        }
                                    }

                                    AnimatedVisibility(visible = symbolRulesExpanded) {
                                        Column {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                                SegmentedButton(
                                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                                    onClick = { viewModel.updateUseSymbolExclusionMode(true) },
                                                    selected = useSymbolExclusionMode,
                                                    enabled = includeSymbols
                                                ) {
                                                    Text(stringResource(R.string.generator_symbols_mode_exclude))
                                                }
                                                SegmentedButton(
                                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                                    onClick = { viewModel.updateUseSymbolExclusionMode(false) },
                                                    selected = !useSymbolExclusionMode,
                                                    enabled = includeSymbols
                                                ) {
                                                    Text(stringResource(R.string.generator_symbols_mode_whitelist))
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))
                                            if (useSymbolExclusionMode) {
                                                Text(
                                                    text = stringResource(R.string.generator_symbols_exclude_hint),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    OutlinedButton(
                                                        onClick = { viewModel.clearExcludedSymbols() },
                                                        enabled = includeSymbols
                                                    ) {
                                                        Text(stringResource(R.string.generator_custom_symbols_reset))
                                                    }
                                                    OutlinedButton(
                                                        onClick = { viewModel.updateExcludedSymbols("&!") },
                                                        enabled = includeSymbols
                                                    ) {
                                                        Text(stringResource(R.string.generator_exclude_amp_bang))
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                FlowRow(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    defaultSymbols.forEach { symbol ->
                                                        FilterChip(
                                                            selected = symbol in excludedSymbolsSet,
                                                            onClick = { viewModel.toggleExcludedSymbol(symbol) },
                                                            enabled = includeSymbols,
                                                            label = { Text(symbol.toString()) }
                                                        )
                                                    }
                                                }
                                            } else {
                                                OutlinedTextField(
                                                    value = customSymbols,
                                                    onValueChange = { viewModel.updateCustomSymbols(it) },
                                                    label = { Text(stringResource(R.string.generator_custom_symbols_label)) },
                                                    placeholder = { Text(stringResource(R.string.generator_custom_symbols_placeholder)) },
                                                    singleLine = true,
                                                    enabled = includeSymbols,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.End
                                                ) {
                                                    TextButton(
                                                        onClick = { viewModel.resetCustomSymbols() },
                                                        enabled = includeSymbols
                                                    ) {
                                                        Text(text = stringResource(R.string.generator_custom_symbols_reset))
                                                    }
                                                }
                                            }

                                            if (includeSymbols && filteredAllowedSymbols.isEmpty()) {
                                                Text(
                                                    text = stringResource(R.string.generator_custom_symbols_empty_error),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                             
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

                            CheckboxWithText(
                                text = stringResource(R.string.analyze_common_passwords),
                                checked = analyzeCommonPasswords,
                                onCheckedChange = { viewModel.updateAnalyzeCommonPasswords(it) }
                            )
                            if (analyzeCommonPasswords) {
                                Text(
                                    text = stringResource(R.string.analyze_common_passwords_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
                                )

                                // 权重滑块：控制保留常见片段的强度
                                Text(
                                    text = stringResource(R.string.analyze_weight_label, analyzeWeight),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                                )
                                Slider(
                                    value = analyzeWeight.toFloat(),
                                    onValueChange = { viewModel.updateAnalyzeWeight(it.toInt()) },
                                    valueRange = 0f..100f,
                                    steps = 10,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 4.dp, end = 4.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.generator_require_symbol),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Switch(
                                checked = includeSymbols && symbolsMin > 0,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        viewModel.updateIncludeSymbols(true)
                                        viewModel.updateSymbolsMin(if (symbolsMin > 0) symbolsMin else 1)
                                    } else {
                                        viewModel.updateSymbolsMin(0)
                                    }
                                },
                                enabled = includeSymbols || symbolsMin > 0
                            )
                        }
                         
                        // 最小字符数要求
                        Text(
                            text = stringResource(R.string.minimum_characters_requirement),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Column {
                            // 大写字母最小数量
                            Text(
                                text = stringResource(R.string.min_uppercase, uppercaseMin),
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
                                text = stringResource(R.string.min_lowercase, lowercaseMin),
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
                                text = stringResource(R.string.min_numbers, numbersMin),
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
                                text = stringResource(R.string.min_symbols, symbolsMin),
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
                            valueRange = 4f..30f,
                            steps = 26,
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
                            valueRange = 0f..20f,  // 0表示不使用分段功能
                            steps = 20,  // 步进值为1
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                    GeneratorType.PASSPHRASE -> {
                    // 密码短语生成器配置选项
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.passphrase_generator),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        // 单词数量滑块
                        Text(
                            text = stringResource(R.string.word_count, passphraseWordCount),
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
                            label = { Text(stringResource(R.string.delimiter)) },
                            placeholder = { Text(stringResource(R.string.delimiter_placeholder)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // 选项设置
                        CheckboxWithText(
                            text = stringResource(R.string.capitalize_first_letter),
                            checked = passphraseCapitalize,
                            onCheckedChange = { viewModel.updatePassphraseCapitalize(it) }
                        )
                        
                        CheckboxWithText(
                            text = stringResource(R.string.add_number_at_end),
                            checked = passphraseIncludeNumber,
                            onCheckedChange = { viewModel.updatePassphraseIncludeNumber(it) }
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // 自定义单词
                        OutlinedTextField(
                            value = passphraseCustomWord,
                            onValueChange = { viewModel.updatePassphraseCustomWord(it) },
                            label = { Text(stringResource(R.string.custom_word_optional)) },
                            placeholder = { Text(stringResource(R.string.custom_word_placeholder)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = passphraseCustomWords,
                            onValueChange = { viewModel.updatePassphraseCustomWords(it) },
                            label = { Text(stringResource(R.string.custom_mnemonic_words_optional)) },
                            placeholder = { Text(stringResource(R.string.custom_mnemonic_words_placeholder)) },
                            supportingText = {
                                Text(stringResource(R.string.custom_mnemonic_words_hint))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4
                        )
                    }
                }
                
                    GeneratorType.PIN -> {
                    // PIN码生成器配置选项
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.pin_generator),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        // 长度滑块
                        Text(
                            text = stringResource(R.string.pin_length, pinLength),
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
                }
            }
        }
        
        if (!useExternalRefreshFab) {
            // 右下角重新生成按钮 - FAB 设计
            var isRegenerating by remember { mutableStateOf(false) }

            FloatingActionButton(
                onClick = {
                    isRegenerating = true
                    regenerateNow()
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                // 旋转动画
                val rotation by animateFloatAsState(
                    targetValue = if (isRegenerating) 360f else 0f,
                    animationSpec = tween(
                        durationMillis = 500,
                        easing = FastOutSlowInEasing
                    ),
                    finishedListener = { isRegenerating = false },
                    label = "refresh_rotation"
                )

                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.regenerate),
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer { rotationZ = rotation }
                )
            }
        }
    }
    
    GeneratorHistorySheet(
        visible = showHistorySheet,
        onDismiss = { showHistorySheet = false },
        onShowFilter = { showFilterSheet = true },
        filteredHistoryList = filteredHistoryList,
        historyManager = historyManager,
        scope = scope,
        context = context,
        passwordViewModel = passwordViewModel
    )

    GeneratorFilterSheet(
        visible = showFilterSheet,
        onDismiss = { showFilterSheet = false },
        filterSettings = filterSettings,
        filterPreferences = filterPreferences,
        scope = scope
    )
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
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) 
                    MaterialTheme.colorScheme.onPrimary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 优化后的结果显示卡片 - 带流畅动画
 */
@Composable
private fun ResultCard(
    result: String,
    modifier: Modifier = Modifier,
    compactMode: Boolean = false,
    onCopy: (String) -> Unit
) {
    var showCopied by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme
    val strengthResult = remember(result) { PasswordGenerator.analyzePasswordStrength(result) }
    val strengthColor = when (strengthResult.level) {
        PasswordGenerator.StrengthLevel.VERY_WEAK -> colorScheme.error
        PasswordGenerator.StrengthLevel.WEAK -> colorScheme.tertiary
        PasswordGenerator.StrengthLevel.FAIR -> colorScheme.secondary
        PasswordGenerator.StrengthLevel.STRONG -> colorScheme.primary
        PasswordGenerator.StrengthLevel.VERY_STRONG -> colorScheme.primary
    }
    
    LaunchedEffect(showCopied) {
        if (showCopied) {
            kotlinx.coroutines.delay(1500)
            showCopied = false
        }
    }
    
    // 渐变动画
    val cardAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "card_alpha"
    )
    
    // 复制按钮背景颜色动画
    val buttonColor by animateColorAsState(
        targetValue = if (showCopied) 
            colorScheme.tertiaryContainer
        else 
            colorScheme.secondaryContainer,
        animationSpec = tween(durationMillis = 300),
        label = "button_color"
    )
    val buttonContentColor by animateColorAsState(
        targetValue = if (showCopied) {
            colorScheme.onTertiaryContainer
        } else {
            colorScheme.onSecondaryContainer
        },
        animationSpec = tween(durationMillis = 300),
        label = "button_content_color"
    )

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            .graphicsLayer { alpha = cardAlpha },
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (compactMode) 0.dp else 4.dp
        ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (compactMode) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                colorScheme.primaryContainer
            }
        )
    ) {
        AnimatedContent(
            targetState = compactMode,
            transitionSpec = {
                (fadeIn(animationSpec = tween(180)) + scaleIn(initialScale = 0.94f)) togetherWith
                    (fadeOut(animationSpec = tween(160)) + scaleOut(targetScale = 1.02f))
            },
            label = "result_card_compact_transition"
        ) { isCompact ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isCompact) 10.dp else 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(if (isCompact) 18.dp else 24.dp)
                        )
                        Text(
                            text = stringResource(R.string.generated_password),
                            style = if (isCompact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isCompact) colorScheme.onSurfaceVariant else colorScheme.onPrimaryContainer,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    FilledTonalButton(
                        onClick = {
                            onCopy(result)
                            showCopied = true
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = buttonColor,
                            contentColor = buttonContentColor
                        ),
                        contentPadding = if (isCompact) {
                            PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        } else {
                            PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        }
                    ) {
                        AnimatedContent(
                            targetState = showCopied,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(200)) togetherWith
                                    fadeOut(animationSpec = tween(200))
                            },
                            label = "icon_animation"
                        ) { copied ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (copied) Icons.Default.Done else Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(if (isCompact) 16.dp else 18.dp)
                                )
                                if (!isCompact) {
                                    Text(
                                        text = if (copied) stringResource(R.string.generator_copied) else stringResource(R.string.copy),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 16.dp))

                AnimatedVisibility(
                    visible = result.isNotEmpty(),
                    enter = fadeIn(animationSpec = tween(300)) + expandVertically(),
                    exit = fadeOut(animationSpec = tween(200)) + shrinkVertically()
                ) {
                    SelectionContainer {
                        Text(
                            text = colorizePassword(result),
                            style = (if (isCompact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall).copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                letterSpacing = androidx.compose.ui.unit.TextUnit(0.5f, androidx.compose.ui.unit.TextUnitType.Sp)
                            ),
                            fontWeight = FontWeight.Medium,
                            lineHeight = if (isCompact) {
                                MaterialTheme.typography.titleMedium.lineHeight
                            } else {
                                MaterialTheme.typography.headlineSmall.lineHeight * 1.4f
                            },
                            maxLines = if (isCompact) 1 else Int.MAX_VALUE
                        )
                    }
                }

                Spacer(modifier = Modifier.height(if (isCompact) 6.dp else 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "安全程度",
                        style = if (isCompact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                        color = if (isCompact) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        }
                    )
                    Surface(
                        shape = RoundedCornerShape(99.dp),
                        color = strengthColor.copy(alpha = if (isCompact) 0.18f else 0.16f),
                        contentColor = strengthColor
                    ) {
                        Text(
                            text = strengthResult.level.displayName,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = if (isCompact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                AnimatedVisibility(
                    visible = result.isNotEmpty(),
                    enter = fadeIn(animationSpec = tween(300, delayMillis = 100)) + slideInVertically(),
                    exit = fadeOut(animationSpec = tween(200))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = if (isCompact) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            },
                            modifier = Modifier.size(if (isCompact) 14.dp else 16.dp)
                        )
                        Text(
                            text = stringResource(R.string.length_chars, result.length),
                            style = if (isCompact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                            color = if (isCompact) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeneratorHistorySheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onShowFilter: () -> Unit,
    filteredHistoryList: List<PasswordGenerationHistory>,
    historyManager: PasswordHistoryManager,
    scope: CoroutineScope,
    context: Context,
    passwordViewModel: PasswordViewModel
) {
    if (!visible) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 标题和过滤按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.history),
                    style = MaterialTheme.typography.titleLarge
                )

                IconButton(
                    onClick = onShowFilter
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = stringResource(R.string.filter_history),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (filteredHistoryList.isEmpty()) {
                // 空状态
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = stringResource(R.string.no_history),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.generator_history_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // 历史记录列表
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredHistoryList) { historyItem ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                // 密码显示和操作按钮
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    var passwordVisible by remember { mutableStateOf(false) }

                                    Text(
                                        text = if (passwordVisible) historyItem.password else "••••••••••",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f)
                                    )

                                    // 显示/隐藏密码按钮
                                    IconButton(
                                        onClick = { passwordVisible = !passwordVisible }
                                    ) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (passwordVisible) context.getString(R.string.hide_password) else context.getString(R.string.show_password),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    var copied by remember { mutableStateOf(false) }

                                    // 复制按钮
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("password", historyItem.password)
                                            clipboard.setPrimaryClip(clip)
                                            copied = true
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (copied) Icons.Default.Done else Icons.Default.ContentCopy,
                                            contentDescription = stringResource(R.string.copy_password_desc),
                                            tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // 删除按钮
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                historyManager.deleteHistory(historyItem.timestamp)
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.delete),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // 应用信息
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Key,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        text = if (historyItem.domain.isNotEmpty()) {
                                            historyItem.domain
                                        } else {
                                            historyItem.packageName
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }

                                // 用户名信息
                                if (historyItem.username.isNotEmpty()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Key,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            text = stringResource(R.string.username_label, historyItem.username),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                        )
                                    }
                                }

                                // 时间戳
                                Text(
                                    text = java.text.SimpleDateFormat(
                                        "yyyy-MM-dd HH:mm:ss",
                                        java.util.Locale.getDefault()
                                    ).format(java.util.Date(historyItem.timestamp)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(top = 4.dp)
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // 保存到密码库按钮
                                val allPasswords by passwordViewModel.passwordEntries.collectAsState()

                                // 检查密码是否已存在（相同密码和域名/包名）
                                val alreadyExists = remember(historyItem.password, historyItem.domain, historyItem.packageName, allPasswords) {
                                    allPasswords.any { entry ->
                                        entry.password == historyItem.password &&
                                            (entry.website == historyItem.domain ||
                                                entry.appPackageName == historyItem.packageName)
                                    }
                                }

                                var saved by remember { mutableStateOf(false) }

                                Button(
                                    onClick = {
                                        if (!alreadyExists) {
                                            scope.launch {
                                                val entry = PasswordEntry(
                                                    title = if (historyItem.domain.isNotEmpty()) {
                                                        historyItem.domain
                                                    } else {
                                                        historyItem.packageName.substringAfterLast('.')
                                                    },
                                                    website = historyItem.domain,
                                                    username = historyItem.username,
                                                    password = historyItem.password,
                                                    notes = context.getString(
                                                        R.string.saved_from_generator_history,
                                                        java.text.SimpleDateFormat(
                                                            "yyyy-MM-dd HH:mm:ss",
                                                            java.util.Locale.getDefault()
                                                        ).format(java.util.Date(historyItem.timestamp))
                                                    ),
                                                    appPackageName = historyItem.packageName,
                                                    createdAt = Date(),
                                                    updatedAt = Date()
                                                )
                                                passwordViewModel.addPasswordEntry(entry)
                                                saved = true
                                                Toast.makeText(context, context.getString(R.string.saved_to_vault), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !alreadyExists && !saved,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (alreadyExists || saved) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = if (alreadyExists || saved) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                ) {
                                    Icon(
                                        imageVector = if (alreadyExists || saved) Icons.Default.Done else Icons.Default.Save,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (alreadyExists) stringResource(R.string.already_in_vault)
                                        else if (saved) stringResource(R.string.saved_to_vault)
                                        else stringResource(R.string.save_to_vault)
                                    )
                                }
                            }
                        }
                    }
                }

                // 清空历史按钮
                Button(
                    onClick = {
                        scope.launch {
                            historyManager.clearHistory()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text(stringResource(R.string.clear_history))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeneratorFilterSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    filterSettings: HistoryFilterSettings,
    filterPreferences: HistoryFilterPreferences,
    scope: CoroutineScope
) {
    if (!visible) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.select_history_types),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = stringResource(R.string.history_filter_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // 过滤选项
            CheckboxWithText(
                text = stringResource(R.string.history_type_symbol),
                checked = filterSettings.showSymbol,
                onCheckedChange = {
                    scope.launch {
                        filterPreferences.updateFilterSettings(
                            filterSettings.copy(showSymbol = it)
                        )
                    }
                }
            )

            CheckboxWithText(
                text = stringResource(R.string.history_type_password),
                checked = filterSettings.showPassword,
                onCheckedChange = {
                    scope.launch {
                        filterPreferences.updateFilterSettings(
                            filterSettings.copy(showPassword = it)
                        )
                    }
                }
            )

            CheckboxWithText(
                text = stringResource(R.string.history_type_passphrase),
                checked = filterSettings.showPassphrase,
                onCheckedChange = {
                    scope.launch {
                        filterPreferences.updateFilterSettings(
                            filterSettings.copy(showPassphrase = it)
                        )
                    }
                }
            )

            CheckboxWithText(
                text = stringResource(R.string.history_type_pin),
                checked = filterSettings.showPin,
                onCheckedChange = {
                    scope.launch {
                        filterPreferences.updateFilterSettings(
                            filterSettings.copy(showPin = it)
                        )
                    }
                }
            )

            CheckboxWithText(
                text = stringResource(R.string.history_type_autofill),
                checked = filterSettings.showAutofill,
                onCheckedChange = {
                    scope.launch {
                        filterPreferences.updateFilterSettings(
                            filterSettings.copy(showAutofill = it)
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 关闭按钮
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.close))
            }
        }
    }
}

private fun applyGeneratorSymbolFilters(
    symbols: String,
    excludeSimilar: Boolean,
    excludeAmbiguous: Boolean
): String {
    var result = symbols
    if (excludeSimilar) {
        result = result.filter { it !in "0OlI1" }
    }
    if (excludeAmbiguous) {
        result = result.filter { it !in "{}[]()/~`'" }
    }
    return result
}
