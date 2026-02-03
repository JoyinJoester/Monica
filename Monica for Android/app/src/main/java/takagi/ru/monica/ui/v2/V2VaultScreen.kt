package takagi.ru.monica.ui.v2

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import takagi.ru.monica.R
import takagi.ru.monica.ui.components.ExpressiveTopBar

/**
 * V2 密码库类型筛选器
 */
enum class V2VaultFilter(val labelRes: Int, val icon: ImageVector) {
    ALL(R.string.v2_filter_all, Icons.Default.Dashboard),
    LOGIN(R.string.v2_filter_login, Icons.Default.Lock),
    CARD(R.string.v2_filter_card, Icons.Default.CreditCard),
    IDENTITY(R.string.v2_filter_identity, Icons.Default.Person),
    NOTE(R.string.v2_filter_note, Icons.Default.Note)
}

/**
 * V2 数据源选择器选项
 */
enum class V2VaultSource(val labelRes: Int, val icon: ImageVector) {
    ALL(R.string.v2_filter_all, Icons.Default.Layers),
    LOCAL(R.string.v2_source_local, Icons.Default.PhoneAndroid),
    BITWARDEN(R.string.v2_source_bitwarden, Icons.Default.Cloud),
    KEEPASS(R.string.v2_source_keepass, Icons.Default.Storage)
}

/**
 * 创建条目类型（FAB 菜单选项）
 */
enum class CreateItemType(
    val labelRes: Int,
    val icon: ImageVector,
    val colorKey: String
) {
    LOGIN(R.string.v2_create_login, Icons.Outlined.Lock, "primary"),
    CARD(R.string.v2_create_card, Icons.Outlined.CreditCard, "tertiary"),
    IDENTITY(R.string.v2_create_identity, Icons.Outlined.Person, "secondary"),
    NOTE(R.string.v2_create_note, Icons.Outlined.Note, "error")
}

/**
 * V2 多源密码库主页面
 * 
 * Bitwarden 风格设计：
 * - 顶部搜索栏
 * - 类型筛选 Chips
 * - 条目列表（按类型分组或平铺）
 * - M3 Expressive FAB 创建菜单
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V2VaultScreen(
    modifier: Modifier = Modifier,
    viewModel: V2ViewModel = viewModel(),
    onNavigateToBitwardenLogin: () -> Unit = {},
    onNavigateToAddEntry: (String) -> Unit = {},
    onItemClick: (V2VaultFilter, Long) -> Unit = { _, _ -> }  // 类型 + ID
) {
    // 从 ViewModel 收集状态
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val selectedSource by viewModel.selectedSource.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filteredEntries by viewModel.filteredEntries.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val sortDirection by viewModel.sortDirection.collectAsState()
    val filterFavorite by viewModel.filterFavorite.collectAsState()
    
    var isSearchExpanded by remember { mutableStateOf(false) }
    var isFabExpanded by remember { mutableStateOf(false) }
    
    // 顶栏弹出菜单状态
    var showFilterMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    
    // 设置开关状态
    var alwaysShowKeyboard by remember { mutableStateOf(false) }
    var rememberSort by remember { mutableStateOf(true) }
    
    // 处理事件
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is V2ViewModel.V2Event.NavigateToBitwardenLogin -> {
                    onNavigateToBitwardenLogin()
                }
                else -> { /* 其他事件处理 */ }
            }
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            val title = when (selectedFilter) {
                V2VaultFilter.ALL -> stringResource(R.string.app_name)
                V2VaultFilter.LOGIN -> stringResource(R.string.v2_filter_login)
                V2VaultFilter.CARD -> stringResource(R.string.v2_filter_card)
                V2VaultFilter.IDENTITY -> stringResource(R.string.v2_filter_identity)
                V2VaultFilter.NOTE -> stringResource(R.string.v2_filter_note)
            }
            
            // V1 同款胶囊搜索顶栏
            ExpressiveTopBar(
                title = title,
                searchQuery = searchQuery,
                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                isSearchExpanded = isSearchExpanded,
                onSearchExpandedChange = { isSearchExpanded = it },
                searchHint = stringResource(R.string.search_passwords_hint),
                actions = {
                    // 1. 筛选条件 (FilterAlt 图标) - 参考 keyguard
                    Box {
                        IconButton(onClick = { showFilterMenu = true }) {
                            Icon(
                                imageVector = Icons.Outlined.FilterAlt,
                                contentDescription = "筛选",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false },
                            modifier = Modifier.widthIn(min = 280.dp),
                            offset = DpOffset(x = 0.dp, y = 0.dp)
                        ) {
                            // 标题：筛选条件
                            Text(
                                text = "筛选条件",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                            
                            // 条目数量
                            Text(
                                text = "${filteredEntries.size} 个条目",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                            
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            
                            // 文件夹分组
                            FilterSectionHeader(title = "文件夹")
                            Text(
                                text = "暂无文件夹",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            
                            // 类型分组
                            FilterSectionHeader(title = "类型")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                V2VaultFilter.entries.drop(1).take(3).forEach { filter ->
                                    FilterChip(
                                        selected = filter == selectedFilter,
                                        onClick = {
                                            if (filter == selectedFilter) {
                                                viewModel.setTypeFilter(V2VaultFilter.ALL)
                                            } else {
                                                viewModel.setTypeFilter(filter)
                                            }
                                        },
                                        label = { Text(stringResource(filter.labelRes), style = MaterialTheme.typography.labelMedium) },
                                        leadingIcon = {
                                            Icon(filter.icon, null, modifier = Modifier.size(16.dp))
                                        }
                                    )
                                }
                            }
                            
                            // 数据源分组
                            FilterSectionHeader(title = "数据源")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = selectedSource == V2VaultSource.LOCAL,
                                    onClick = { 
                                        if (selectedSource == V2VaultSource.LOCAL) {
                                            viewModel.setSourceFilter(V2VaultSource.ALL)
                                        } else {
                                            viewModel.setSourceFilter(V2VaultSource.LOCAL)
                                        }
                                    },
                                    label = { Text("本地", style = MaterialTheme.typography.labelMedium) },
                                    leadingIcon = { Icon(Icons.Default.PhoneAndroid, null, modifier = Modifier.size(16.dp)) }
                                )
                                FilterChip(
                                    selected = selectedSource == V2VaultSource.BITWARDEN,
                                    onClick = { 
                                        if (selectedSource == V2VaultSource.BITWARDEN) {
                                            viewModel.setSourceFilter(V2VaultSource.ALL)
                                        } else {
                                            viewModel.setSourceFilter(V2VaultSource.BITWARDEN)
                                        }
                                    },
                                    label = { Text("Bitwarden", style = MaterialTheme.typography.labelMedium) },
                                    leadingIcon = { Icon(Icons.Default.Cloud, null, modifier = Modifier.size(16.dp)) }
                                )
                                FilterChip(
                                    selected = selectedSource == V2VaultSource.KEEPASS,
                                    onClick = { 
                                        if (selectedSource == V2VaultSource.KEEPASS) {
                                            viewModel.setSourceFilter(V2VaultSource.ALL)
                                        } else {
                                            viewModel.setSourceFilter(V2VaultSource.KEEPASS)
                                        }
                                    },
                                    label = { Text("KeePass", style = MaterialTheme.typography.labelMedium) },
                                    leadingIcon = { Icon(Icons.Default.Storage, null, modifier = Modifier.size(16.dp)) }
                                )
                            }
                            
                            // 杂项分组
                            FilterSectionHeader(title = "杂项")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = false,
                                    onClick = { viewModel.toggleTotpFilter() },
                                    label = { Text("有 TOTP", style = MaterialTheme.typography.labelMedium) },
                                    leadingIcon = { Text("2FA", style = MaterialTheme.typography.labelSmall) }
                                )
                                FilterChip(
                                    selected = filterFavorite,
                                    onClick = { viewModel.toggleFavoriteFilter() },
                                    label = { Text("标星", style = MaterialTheme.typography.labelMedium) },
                                    leadingIcon = { Icon(Icons.Default.Star, null, modifier = Modifier.size(16.dp)) }
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    
                    // 2. 排序方式 (SortByAlpha 图标) - 参考 keyguard
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                imageVector = Icons.Outlined.SortByAlpha,
                                contentDescription = "排序",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            modifier = Modifier.widthIn(min = 240.dp),
                            offset = DpOffset(x = 0.dp, y = 0.dp)
                        ) {
                            // 标题：排序方式
                            Text(
                                text = "排序方式",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                            
                            // 排序选项（单选风格）
                            SortMenuItem(
                                text = "标题",
                                icon = Icons.Default.SortByAlpha,
                                selected = sortMode == V2SortMode.TITLE,
                                onClick = { 
                                    viewModel.setSortMode(V2SortMode.TITLE)
                                    showSortMenu = false 
                                }
                            )
                            SortMenuItem(
                                text = "按修改日期排序",
                                icon = Icons.Default.EditCalendar,
                                selected = sortMode == V2SortMode.MODIFIED_DATE,
                                onClick = { 
                                    viewModel.setSortMode(V2SortMode.MODIFIED_DATE)
                                    showSortMenu = false 
                                }
                            )
                            SortMenuItem(
                                text = "按密码排序",
                                icon = Icons.Default.Password,
                                selected = sortMode == V2SortMode.PASSWORD,
                                onClick = { 
                                    viewModel.setSortMode(V2SortMode.PASSWORD)
                                    showSortMenu = false 
                                }
                            )
                            SortMenuItem(
                                text = "按密码修改日期排序",
                                icon = Icons.Default.CalendarMonth,
                                selected = sortMode == V2SortMode.PASSWORD_DATE,
                                onClick = { 
                                    viewModel.setSortMode(V2SortMode.PASSWORD_DATE)
                                    showSortMenu = false 
                                }
                            )
                            SortMenuItem(
                                text = "按密码强度排序",
                                icon = Icons.Default.Security,
                                selected = sortMode == V2SortMode.PASSWORD_STRENGTH,
                                onClick = { 
                                    viewModel.setSortMode(V2SortMode.PASSWORD_STRENGTH)
                                    showSortMenu = false 
                                }
                            )
                            
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            
                            // 选项
                            Text(
                                text = "选项",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            
                            SortMenuItem(
                                text = "按字母排序",
                                icon = null,
                                selected = sortDirection == V2SortDirection.ASCENDING,
                                onClick = { 
                                    viewModel.setSortDirection(V2SortDirection.ASCENDING)
                                    showSortMenu = false 
                                }
                            )
                            SortMenuItem(
                                text = "按字母反向排序",
                                icon = null,
                                selected = sortDirection == V2SortDirection.DESCENDING,
                                onClick = { 
                                    viewModel.setSortDirection(V2SortDirection.DESCENDING)
                                    showSortMenu = false 
                                }
                            )
                        }
                    }
                    
                    // 3. 更多选项 (MoreVert 三个点) - 参考 keyguard
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "更多",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                            modifier = Modifier.widthIn(min = 220.dp),
                            offset = DpOffset(x = 0.dp, y = 0.dp)
                        ) {
                            // 导航项
                            DropdownMenuItem(
                                text = { Text("回收站") },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                trailingIcon = { Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(20.dp)) },
                                onClick = { showMoreMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("下载") },
                                leadingIcon = { Icon(Icons.Outlined.Download, null) },
                                trailingIcon = { Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(20.dp)) },
                                onClick = { showMoreMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("自定义筛选规则") },
                                leadingIcon = { Icon(Icons.Outlined.FilterList, null) },
                                trailingIcon = { Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(20.dp)) },
                                onClick = { showMoreMenu = false }
                            )
                            
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            
                            // 开关项
                            DropdownMenuItem(
                                text = { Text("总是显示键盘") },
                                leadingIcon = { Icon(Icons.Outlined.Keyboard, null) },
                                trailingIcon = {
                                    Switch(
                                        checked = alwaysShowKeyboard,
                                        onCheckedChange = { alwaysShowKeyboard = it },
                                        modifier = Modifier.scale(0.7f)
                                    )
                                },
                                onClick = { alwaysShowKeyboard = !alwaysShowKeyboard }
                            )
                            DropdownMenuItem(
                                text = { Text("记住排序方法") },
                                leadingIcon = { Icon(Icons.Outlined.SortByAlpha, null) },
                                trailingIcon = {
                                    Switch(
                                        checked = rememberSort,
                                        onCheckedChange = { rememberSort = it },
                                        modifier = Modifier.scale(0.7f)
                                    )
                                },
                                onClick = { rememberSort = !rememberSort }
                            )
                            
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            
                            // 操作项
                            DropdownMenuItem(
                                text = { Text("同步密码库") },
                                leadingIcon = { Icon(Icons.Default.Sync, null) },
                                onClick = {
                                    viewModel.refresh()
                                    showMoreMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("锁定密码库") },
                                leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                                onClick = { showMoreMenu = false }
                            )
                        }
                    }
                    
                    // 4. 搜索 Trigger (放在最右边)
                    IconButton(onClick = { isSearchExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "搜索",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
            
            // 类型筛选 Chips
            V2FilterChips(
                selectedFilter = selectedFilter,
                onFilterSelected = { viewModel.setTypeFilter(it) },
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            // 条目列表
            if (filteredEntries.isEmpty()) {
                V2EmptyState(
                    filter = selectedFilter,
                    modifier = Modifier.weight(1f)
                )
            } else {
                V2EntryList(
                    entries = filteredEntries,
                    onItemClick = { entry ->
                        // 传递类型和ID，让导航层决定去哪个编辑页面
                        onItemClick(entry.type, entry.id)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        // 遮罩层（FAB 展开时显示）
        AnimatedVisibility(
            visible = isFabExpanded,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { isFabExpanded = false }
            )
        }
        
        // FAB 创建菜单
        V2CreateFab(
            isExpanded = isFabExpanded,
            onExpandedChange = { isFabExpanded = it },
            onCreateItem = { type ->
                isFabExpanded = false
                onNavigateToAddEntry(type.name)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
}

/**
 * V2 搜索栏
 */
@Composable
private fun V2SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = stringResource(R.string.v2_search_placeholder),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "清除"
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    )
}

/**
 * V2 类型筛选 Chips
 */
@Composable
private fun V2FilterChips(
    selectedFilter: V2VaultFilter,
    onFilterSelected: (V2VaultFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        V2VaultFilter.values().forEach { filter ->
            FilterChip(
                selected = filter == selectedFilter,
                onClick = { onFilterSelected(filter) },
                label = {
                    Text(
                        text = stringResource(filter.labelRes),
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = filter.icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

/**
 * V2 条目列表
 */
@Composable
private fun V2EntryList(
    entries: List<V2ViewModel.UnifiedEntry>,
    onItemClick: (V2ViewModel.UnifiedEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(entries, key = { "${it.source.name}_${it.id}" }) { entry ->
            V2EntryItem(
                entry = entry,
                onClick = { onItemClick(entry) }
            )
        }
        
        // 底部留白（给 FAB 留空间）
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

/**
 * V2 条目项
 */
@Composable
private fun V2EntryItem(
    entry: V2ViewModel.UnifiedEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 类型图标
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(getTypeColor(entry.type).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = entry.type.icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = getTypeColor(entry.type)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 标题和副标题
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.subtitle.isNotBlank()) {
                    Text(
                        text = entry.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // 来源标识图标
            if (entry.source != V2VaultSource.ALL) {
                Icon(
                    imageVector = entry.source.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            // 更多菜单
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "更多",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * V2 空状态
 */
@Composable
private fun V2EmptyState(
    filter: V2VaultFilter,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Shield,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = when (filter) {
                V2VaultFilter.ALL -> "密码库为空"
                V2VaultFilter.LOGIN -> "没有登录凭证"
                V2VaultFilter.CARD -> "没有支付卡"
                V2VaultFilter.IDENTITY -> "没有身份信息"
                V2VaultFilter.NOTE -> "没有安全笔记"
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "点击 + 按钮创建新条目",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

/**
 * V2 创建 FAB（M3 Expressive 设计）
 * 
 * 参考 Keyguard 的展开菜单，但使用 M3 Expressive 风格
 * 点击展开显示创建选项（登录、卡片、身份、备注）
 */
@Composable
private fun V2CreateFab(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onCreateItem: (CreateItemType) -> Unit,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 45f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "fabRotation"
    )
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 展开的选项（从下往上显示）
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(tween(150)) + 
                    slideInVertically(tween(200)) { it / 2 } + 
                    expandVertically(tween(200)),
            exit = fadeOut(tween(100)) + 
                   slideOutVertically(tween(150)) { it / 2 } + 
                   shrinkVertically(tween(150))
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CreateItemType.values().reversed().forEachIndexed { index, type ->
                    V2CreateOptionItem(
                        type = type,
                        onClick = { onCreateItem(type) },
                        delayMillis = index * 50
                    )
                }
            }
        }
        
        // 主 FAB（使用普通尺寸）
        FloatingActionButton(
            onClick = { onExpandedChange(!isExpanded) },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = if (isExpanded) "关闭" else "新建",
                modifier = Modifier
                    .size(24.dp)
                    .rotate(rotation)
            )
        }
    }
}

/**
 * 创建选项项（FAB 菜单中的单个选项）
 */
@Composable
private fun V2CreateOptionItem(
    type: CreateItemType,
    onClick: () -> Unit,
    delayMillis: Int = 0,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delayMillis.toLong())
        visible = true
    }
    
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "optionScale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(150),
        label = "optionAlpha"
    )
    
    val tintColor = getCreateItemColor(type)
    
    Row(
        modifier = modifier
            .scale(scale)
            .graphicsLayer { this.alpha = alpha },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 标签
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp
        ) {
            Text(
                text = stringResource(type.labelRes),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
        
        // 图标按钮（小尺寸）
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = tintColor,
            contentColor = Color.White
        ) {
            Icon(
                imageVector = type.icon,
                contentDescription = stringResource(type.labelRes),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 获取类型对应的颜色
 */
@Composable
private fun getTypeColor(type: V2VaultFilter): Color {
    return when (type) {
        V2VaultFilter.ALL -> MaterialTheme.colorScheme.primary
        V2VaultFilter.LOGIN -> MaterialTheme.colorScheme.primary
        V2VaultFilter.CARD -> MaterialTheme.colorScheme.tertiary
        V2VaultFilter.IDENTITY -> MaterialTheme.colorScheme.secondary
        V2VaultFilter.NOTE -> MaterialTheme.colorScheme.error
    }
}

/**
 * 获取创建项类型对应的颜色
 */
@Composable
private fun getCreateItemColor(type: CreateItemType): Color {
    return when (type.colorKey) {
        "primary" -> MaterialTheme.colorScheme.primary
        "secondary" -> MaterialTheme.colorScheme.secondary
        "tertiary" -> MaterialTheme.colorScheme.tertiary
        "error" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
}

// ============ 兼容性保留 ============

/**
 * V2 密码库条目卡片（保留兼容）
 */
@Composable
fun V2VaultItemCard(
    title: String,
    subtitle: String,
    source: V2VaultSource,
    itemType: V2VaultFilter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 类型图标
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = itemType.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // 标题和副标题
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // 来源标识
            if (source != V2VaultSource.ALL) {
                Icon(
                    imageVector = source.icon,
                    contentDescription = stringResource(source.labelRes),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * 筛选分组标题
 */
@Composable
private fun FilterSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/**
 * 排序菜单项 - 带单选样式
 */
@Composable
private fun SortMenuItem(
    text: String,
    icon: ImageVector?,
    selected: Boolean,
    onClick: () -> Unit,
    highlighted: Boolean = false
) {
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary 
                        else MaterialTheme.colorScheme.onSurface
            )
        },
        leadingIcon = {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary 
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(modifier = Modifier.size(24.dp))
            }
        },
        trailingIcon = {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        onClick = onClick
    )
}