package takagi.ru.monica.ui

import android.content.Context
import takagi.ru.monica.R
import takagi.ru.monica.data.Category
import takagi.ru.monica.utils.KeePassGroupInfo
import takagi.ru.monica.utils.decodeKeePassPathForDisplay
import takagi.ru.monica.viewmodel.CategoryFilter

internal data class PasswordQuickFolderNode(
    val category: Category,
    val path: String,
    val parentPath: String?,
    val displayName: String
)

internal data class PasswordQuickFolderShortcut(
    val key: String,
    val title: String,
    val subtitle: String,
    val isBack: Boolean,
    val targetFilter: CategoryFilter,
    val passwordCount: Int?
)

internal data class PasswordQuickFolderBreadcrumb(
    val key: String,
    val title: String,
    val targetFilter: CategoryFilter,
    val isCurrent: Boolean
)

internal fun normalizePasswordQuickFolderPath(path: String): String {
    return path
        .split("/")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString("/")
}

internal fun buildLocalQuickFolderPasswordCountByCategoryId(
    entries: List<takagi.ru.monica.data.PasswordEntry>,
    categories: List<Category>
): Map<Long, Int> {
    if (entries.isEmpty() || categories.isEmpty()) return emptyMap()

    val categoryPathById = categories
        .asSequence()
        .mapNotNull { category ->
            val normalizedPath = normalizePasswordQuickFolderPath(category.name)
            if (normalizedPath.isBlank()) {
                null
            } else {
                category.id to normalizedPath
            }
        }
        .toMap()
    if (categoryPathById.isEmpty()) return emptyMap()

    val aggregatedCountByPath = mutableMapOf<String, Int>()
    entries
        .asSequence()
        .filter { entry -> entry.isLocalOnlyEntry() }
        .mapNotNull { entry -> entry.categoryId?.let(categoryPathById::get) }
        .forEach { path ->
            var prefix = ""
            path.split('/').forEach { segment ->
                prefix = if (prefix.isEmpty()) segment else "$prefix/$segment"
                aggregatedCountByPath[prefix] = (aggregatedCountByPath[prefix] ?: 0) + 1
            }
        }
    if (aggregatedCountByPath.isEmpty()) return emptyMap()

    return buildPasswordQuickFolderNodes(categories)
        .associate { node ->
            node.category.id to (aggregatedCountByPath[node.path] ?: 0)
        }
}

internal fun passwordQuickFolderParentPath(path: String): String? {
    val normalized = normalizePasswordQuickFolderPath(path)
    if (!normalized.contains('/')) return null
    return normalized.substringBeforeLast('/').ifBlank { null }
}

internal fun buildPasswordQuickFolderNodes(categories: List<Category>): List<PasswordQuickFolderNode> {
    return categories
        .sortedWith(compareBy<Category>({ it.sortOrder }, { it.id }))
        .mapNotNull { category ->
            val normalizedPath = normalizePasswordQuickFolderPath(category.name)
            if (normalizedPath.isBlank()) {
                null
            } else {
                PasswordQuickFolderNode(
                    category = category,
                    path = normalizedPath,
                    parentPath = passwordQuickFolderParentPath(normalizedPath),
                    displayName = normalizedPath.substringAfterLast('/')
                )
            }
        }
        .distinctBy { it.path }
}

internal fun buildQuickFolderShortcuts(
    context: Context,
    quickFoldersEnabledForCurrentFilter: Boolean,
    includeBackNavigation: Boolean,
    currentFilter: CategoryFilter,
    quickFolderStyle: takagi.ru.monica.data.PasswordListQuickFolderStyle,
    quickFolderCurrentPath: String?,
    quickFolderNodes: List<PasswordQuickFolderNode>,
    quickFolderNodeByPath: Map<String, PasswordQuickFolderNode>,
    quickFolderRootFilter: CategoryFilter,
    quickFolderPasswordCountByCategoryId: Map<Long, Int>,
    allPasswords: List<takagi.ru.monica.data.PasswordEntry>,
    searchScopedPasswords: List<takagi.ru.monica.data.PasswordEntry>,
    isSearchActive: Boolean,
    keepassDatabases: List<takagi.ru.monica.data.LocalKeePassDatabase>,
    keepassGroupsForSelectedDb: List<KeePassGroupInfo>,
    bitwardenVaults: List<takagi.ru.monica.data.bitwarden.BitwardenVault>,
    selectedBitwardenFolders: List<takagi.ru.monica.data.bitwarden.BitwardenFolder>,
    categories: List<Category>
): List<PasswordQuickFolderShortcut> {
    if (!quickFoldersEnabledForCurrentFilter || !currentFilter.supportsQuickFolders()) {
        return emptyList()
    }

    val shortcuts = mutableListOf<PasswordQuickFolderShortcut>()
    val quickFolderSourceEntries = if (isSearchActive) searchScopedPasswords else allPasswords
    if (includeBackNavigation && currentFilter is CategoryFilter.Custom) {
        val parentPath = quickFolderCurrentPath?.let(::passwordQuickFolderParentPath)
        val parentTarget = if (parentPath != null) {
            quickFolderNodeByPath[parentPath]?.category?.let { CategoryFilter.Custom(it.id) }
                ?: quickFolderRootFilter
        } else {
            quickFolderRootFilter
        }
        shortcuts += PasswordQuickFolderShortcut(
            key = "back_${quickFolderCurrentPath.orEmpty()}",
            title = context.getString(R.string.password_list_quick_folder_back),
            subtitle = context.getString(R.string.password_list_quick_folder_back_subtitle),
            isBack = true,
            targetFilter = parentTarget,
            passwordCount = null
        )
    }

    val shouldShowMonicaFolderShortcuts = when (currentFilter) {
        is CategoryFilter.All,
        is CategoryFilter.Local,
        is CategoryFilter.Starred,
        is CategoryFilter.Uncategorized,
        is CategoryFilter.LocalStarred,
        is CategoryFilter.LocalUncategorized,
        is CategoryFilter.Custom -> true
        else -> false
    }

    if (shouldShowMonicaFolderShortcuts) {
        val targetParentPath = quickFolderCurrentPath
        val children = quickFolderNodes.filter { node -> node.parentPath == targetParentPath }
        children.forEach { node ->
            val passwordCount = quickFolderPasswordCountByCategoryId[node.category.id] ?: 0
            if (isSearchActive && passwordCount <= 0) return@forEach
            shortcuts += PasswordQuickFolderShortcut(
                key = "folder_${node.category.id}_${node.path}",
                title = node.displayName,
                subtitle = "Monica",
                isBack = false,
                targetFilter = CategoryFilter.Custom(node.category.id),
                passwordCount = passwordCount
            )
        }
    }

    when (val filter = currentFilter) {
        is CategoryFilter.KeePassDatabase -> {
            shortcuts += buildKeePassDatabaseQuickFolderShortcuts(
                databaseId = filter.databaseId,
                keepassGroups = keepassGroupsForSelectedDb,
                allPasswords = quickFolderSourceEntries,
                isSearchActive = isSearchActive
            )
        }

        is CategoryFilter.KeePassGroupFilter -> {
            val databaseId = filter.databaseId
            val currentPath = filter.groupPath.trim('/').trim()
            val parentPath = currentPath.substringBeforeLast('/', missingDelimiterValue = "")
            if (includeBackNavigation) {
                val backTarget = if (parentPath.isBlank()) {
                    CategoryFilter.KeePassDatabase(databaseId)
                } else {
                    CategoryFilter.KeePassGroupFilter(databaseId, parentPath)
                }
                shortcuts += PasswordQuickFolderShortcut(
                    key = "back_keepass_${databaseId}_$currentPath",
                    title = context.getString(R.string.password_list_quick_folder_back),
                    subtitle = context.getString(R.string.password_list_quick_folder_back_subtitle),
                    isBack = true,
                    targetFilter = backTarget,
                    passwordCount = null
                )
            }
            shortcuts += buildKeePassGroupQuickFolderShortcuts(
                databaseId = databaseId,
                currentPath = currentPath,
                keepassGroups = keepassGroupsForSelectedDb,
                allPasswords = quickFolderSourceEntries,
                isSearchActive = isSearchActive
            )
        }

        is CategoryFilter.BitwardenVault,
        is CategoryFilter.BitwardenFolderFilter -> {
            val vaultId = when (filter) {
                is CategoryFilter.BitwardenVault -> filter.vaultId
                is CategoryFilter.BitwardenFolderFilter -> filter.vaultId
                else -> error("Unsupported Bitwarden quick-folder filter: $filter")
            }
            val syncedFolderNameById = selectedBitwardenFolders
                .asSequence()
                .map { it.bitwardenFolderId.trim() to it.name.trim() }
                .filter { (folderId, folderName) -> folderId.isNotBlank() && folderName.isNotBlank() }
                .toMap()
            val linkedFolderNameByKey = categories
                .asSequence()
                .mapNotNull { category ->
                    val categoryVaultId = category.bitwardenVaultId
                    val folderId = category.bitwardenFolderId?.trim()?.takeIf { it.isNotBlank() }
                    if (categoryVaultId == vaultId && folderId != null) folderId to category.name else null
                }
                .toMap()
            val folderCountById = quickFolderSourceEntries
                .asSequence()
                .mapNotNull { entry ->
                    val entryVaultId = entry.bitwardenVaultId
                    val folderId = entry.bitwardenFolderId?.trim()?.takeIf { it.isNotBlank() }
                    if (entryVaultId == vaultId && folderId != null) folderId else null
                }
                .groupingBy { it }
                .eachCount()
            val knownFolderIds = if (isSearchActive) {
                folderCountById.keys.sorted()
            } else {
                (folderCountById.keys + linkedFolderNameByKey.keys + syncedFolderNameById.keys)
                    .toSet()
                    .sorted()
            }

            knownFolderIds.forEach { folderId ->
                val folderName = syncedFolderNameById[folderId]
                    ?: linkedFolderNameByKey[folderId]
                    ?: "Folder ${folderId.take(8)}"
                shortcuts += PasswordQuickFolderShortcut(
                    key = "bitwarden_${vaultId}_${folderId}",
                    title = folderName,
                    subtitle = "Bitwarden 文件夹",
                    isBack = false,
                    targetFilter = CategoryFilter.BitwardenFolderFilter(folderId = folderId, vaultId = vaultId),
                    passwordCount = folderCountById[folderId] ?: 0
                )
            }
        }

        else -> Unit
    }

    if (currentFilter is CategoryFilter.All && quickFolderCurrentPath == null) {
        appendRootDatabaseShortcuts(
            shortcuts = shortcuts,
            currentFilter = currentFilter,
            quickFolderSourceEntries = quickFolderSourceEntries,
            isSearchActive = isSearchActive,
            keepassDatabases = keepassDatabases,
            bitwardenVaults = bitwardenVaults,
            categories = categories
        )
    }

    return when (quickFolderStyle) {
        takagi.ru.monica.data.PasswordListQuickFolderStyle.CLASSIC,
        takagi.ru.monica.data.PasswordListQuickFolderStyle.M3_CARD -> shortcuts
    }
}

internal fun buildCategoryMenuFolderShortcuts(
    context: Context,
    currentFilter: CategoryFilter,
    quickFolderCurrentPath: String?,
    quickFolderNodes: List<PasswordQuickFolderNode>,
    quickFolderNodeByPath: Map<String, PasswordQuickFolderNode>,
    quickFolderPasswordCountByCategoryId: Map<Long, Int>,
    allPasswords: List<takagi.ru.monica.data.PasswordEntry>,
    searchScopedPasswords: List<takagi.ru.monica.data.PasswordEntry>,
    isSearchActive: Boolean,
    keepassDatabases: List<takagi.ru.monica.data.LocalKeePassDatabase>,
    keepassGroupsForSelectedDb: List<KeePassGroupInfo>,
    bitwardenVaults: List<takagi.ru.monica.data.bitwarden.BitwardenVault>,
    selectedBitwardenFolders: List<takagi.ru.monica.data.bitwarden.BitwardenFolder>,
    categories: List<Category>
): List<PasswordQuickFolderShortcut> {
    if (!currentFilter.supportsQuickFolders()) return emptyList()

    val shortcuts = mutableListOf<PasswordQuickFolderShortcut>()
    val menuSourceEntries = if (isSearchActive) searchScopedPasswords else allPasswords

    if (currentFilter is CategoryFilter.Custom) {
        val parentPath = quickFolderCurrentPath?.let(::passwordQuickFolderParentPath)
        val parentTarget = if (parentPath != null) {
            quickFolderNodeByPath[parentPath]?.category?.let { CategoryFilter.Custom(it.id) }
                ?: CategoryFilter.Local
        } else {
            CategoryFilter.Local
        }
        shortcuts += PasswordQuickFolderShortcut(
            key = "menu_back_${quickFolderCurrentPath.orEmpty()}",
            title = context.getString(R.string.password_list_quick_folder_back),
            subtitle = context.getString(R.string.password_list_quick_folder_back_subtitle),
            isBack = true,
            targetFilter = parentTarget,
            passwordCount = null
        )
    }

    val shouldShowMonicaFolders = when (currentFilter) {
        is CategoryFilter.All,
        is CategoryFilter.Local,
        is CategoryFilter.Starred,
        is CategoryFilter.Uncategorized,
        is CategoryFilter.LocalStarred,
        is CategoryFilter.LocalUncategorized,
        is CategoryFilter.Custom -> true
        else -> false
    }

    if (shouldShowMonicaFolders) {
        val children = quickFolderNodes.filter { node -> node.parentPath == quickFolderCurrentPath }
        children.forEach { node ->
            val passwordCount = quickFolderPasswordCountByCategoryId[node.category.id] ?: 0
            if (isSearchActive && passwordCount <= 0) return@forEach
            shortcuts += PasswordQuickFolderShortcut(
                key = "menu_folder_${node.category.id}_${node.path}",
                title = node.displayName,
                subtitle = "Monica",
                isBack = false,
                targetFilter = CategoryFilter.Custom(node.category.id),
                passwordCount = passwordCount
            )
        }
    }

    when (val filter = currentFilter) {
        is CategoryFilter.KeePassDatabase -> {
            shortcuts += buildKeePassDatabaseQuickFolderShortcuts(
                databaseId = filter.databaseId,
                keepassGroups = keepassGroupsForSelectedDb,
                allPasswords = menuSourceEntries,
                isSearchActive = isSearchActive
            )
        }

        is CategoryFilter.KeePassGroupFilter -> {
            val currentPath = filter.groupPath.trim('/').trim()
            val parentPath = currentPath.substringBeforeLast('/', missingDelimiterValue = "")
            val backTarget = if (parentPath.isBlank()) {
                CategoryFilter.KeePassDatabase(filter.databaseId)
            } else {
                CategoryFilter.KeePassGroupFilter(filter.databaseId, parentPath)
            }
            shortcuts += PasswordQuickFolderShortcut(
                key = "menu_back_keepass_${filter.databaseId}_$currentPath",
                title = context.getString(R.string.password_list_quick_folder_back),
                subtitle = context.getString(R.string.password_list_quick_folder_back_subtitle),
                isBack = true,
                targetFilter = backTarget,
                passwordCount = null
            )
            shortcuts += buildKeePassGroupQuickFolderShortcuts(
                databaseId = filter.databaseId,
                currentPath = currentPath,
                keepassGroups = keepassGroupsForSelectedDb,
                allPasswords = menuSourceEntries,
                isSearchActive = isSearchActive
            )
        }

        is CategoryFilter.BitwardenVault,
        is CategoryFilter.BitwardenFolderFilter -> {
            val vaultId = when (filter) {
                is CategoryFilter.BitwardenVault -> filter.vaultId
                is CategoryFilter.BitwardenFolderFilter -> filter.vaultId
                else -> error("Unsupported Bitwarden category-menu filter: $filter")
            }
            val selectedFolderId = (filter as? CategoryFilter.BitwardenFolderFilter)?.folderId
            val syncedFolderNameById = selectedBitwardenFolders
                .asSequence()
                .map { it.bitwardenFolderId.trim() to it.name.trim() }
                .filter { (folderId, folderName) -> folderId.isNotBlank() && folderName.isNotBlank() }
                .toMap()
            val linkedFolderNameById = categories
                .asSequence()
                .mapNotNull { category ->
                    val folderId = category.bitwardenFolderId?.trim()?.takeIf { it.isNotBlank() }
                    if (category.bitwardenVaultId == vaultId && folderId != null) folderId to category.name else null
                }
                .toMap()
            val folderCountById = menuSourceEntries
                .asSequence()
                .mapNotNull { entry ->
                    val entryVaultId = entry.bitwardenVaultId
                    val folderId = entry.bitwardenFolderId?.trim()?.takeIf { it.isNotBlank() }
                    if (entryVaultId == vaultId && folderId != null) folderId else null
                }
                .groupingBy { it }
                .eachCount()
            val knownFolderIds = if (isSearchActive) {
                folderCountById.keys.sorted()
            } else {
                (folderCountById.keys + linkedFolderNameById.keys + syncedFolderNameById.keys)
                    .toSet()
                    .sorted()
            }

            val backTarget = if (filter is CategoryFilter.BitwardenFolderFilter) {
                CategoryFilter.BitwardenVault(vaultId)
            } else {
                null
            }
            if (backTarget != null) {
                shortcuts += PasswordQuickFolderShortcut(
                    key = "menu_back_bitwarden_${vaultId}_${selectedFolderId.orEmpty()}",
                    title = context.getString(R.string.password_list_quick_folder_back),
                    subtitle = context.getString(R.string.password_list_quick_folder_back_subtitle),
                    isBack = true,
                    targetFilter = backTarget,
                    passwordCount = null
                )
            }

            knownFolderIds.forEach { folderId ->
                val folderName = syncedFolderNameById[folderId]
                    ?: linkedFolderNameById[folderId]
                    ?: "Folder ${folderId.take(8)}"
                shortcuts += PasswordQuickFolderShortcut(
                    key = "menu_bitwarden_${vaultId}_${folderId}",
                    title = folderName,
                    subtitle = "Bitwarden 文件夹",
                    isBack = false,
                    targetFilter = CategoryFilter.BitwardenFolderFilter(folderId = folderId, vaultId = vaultId),
                    passwordCount = folderCountById[folderId] ?: 0
                )
            }
        }

        else -> Unit
    }

    return shortcuts
}

internal fun buildQuickFolderBreadcrumbs(
    context: Context,
    quickFolderPathBannerEnabledForCurrentFilter: Boolean,
    currentFilter: CategoryFilter,
    quickFolderCurrentPath: String?,
    quickFolderNodeByPath: Map<String, PasswordQuickFolderNode>,
    quickFolderRootFilter: CategoryFilter,
    keepassDatabases: List<takagi.ru.monica.data.LocalKeePassDatabase>,
    bitwardenVaults: List<takagi.ru.monica.data.bitwarden.BitwardenVault>,
    selectedBitwardenFolders: List<takagi.ru.monica.data.bitwarden.BitwardenFolder>,
    categories: List<Category>
): List<PasswordQuickFolderBreadcrumb> {
    if (!quickFolderPathBannerEnabledForCurrentFilter || !currentFilter.supportsQuickFolderBreadcrumbs()) {
        return emptyList()
    }

    val crumbs = mutableListOf<PasswordQuickFolderBreadcrumb>()
    when (val filter = currentFilter) {
        is CategoryFilter.KeePassDatabase -> {
            val databaseName = keepassDatabases.find { it.id == filter.databaseId }?.name ?: "KeePass"
            crumbs += PasswordQuickFolderBreadcrumb("root_keepass_${filter.databaseId}", databaseName, filter, true)
        }

        is CategoryFilter.KeePassGroupFilter -> {
            val databaseName = keepassDatabases.find { it.id == filter.databaseId }?.name ?: "KeePass"
            crumbs += PasswordQuickFolderBreadcrumb(
                key = "root_keepass_${filter.databaseId}",
                title = databaseName,
                targetFilter = CategoryFilter.KeePassDatabase(filter.databaseId),
                isCurrent = false
            )
        }

        is CategoryFilter.BitwardenVault -> {
            val vaultName = bitwardenVaults.find { it.id == filter.vaultId }?.email ?: "Bitwarden"
            crumbs += PasswordQuickFolderBreadcrumb("root_bitwarden_${filter.vaultId}", vaultName, filter, true)
        }

        is CategoryFilter.BitwardenFolderFilter -> {
            val vaultName = bitwardenVaults.find { it.id == filter.vaultId }?.email ?: "Bitwarden"
            crumbs += PasswordQuickFolderBreadcrumb(
                key = "root_bitwarden_${filter.vaultId}",
                title = vaultName,
                targetFilter = CategoryFilter.BitwardenVault(filter.vaultId),
                isCurrent = false
            )
        }

        else -> {
            val rootTitle = if (quickFolderRootFilter is CategoryFilter.All) {
                "ALL"
            } else {
                context.getString(R.string.password_list_quick_folder_root_label)
            }
            crumbs += PasswordQuickFolderBreadcrumb("root", rootTitle, quickFolderRootFilter, quickFolderCurrentPath == null)
        }
    }

    when (val filter = currentFilter) {
        is CategoryFilter.Custom -> {
            if (quickFolderRootFilter is CategoryFilter.All) {
                crumbs += PasswordQuickFolderBreadcrumb("source_monica", "Monica", CategoryFilter.Local, quickFolderCurrentPath == null)
            }
            val path = quickFolderCurrentPath
            if (!path.isNullOrBlank()) {
                var cumulative = ""
                val parts = path.split("/").filter { it.isNotBlank() }
                parts.forEachIndexed { index, part ->
                    cumulative = if (cumulative.isBlank()) part else "$cumulative/$part"
                    val targetFilter = quickFolderNodeByPath[cumulative]?.category?.let { CategoryFilter.Custom(it.id) }
                    if (targetFilter != null) {
                        crumbs += PasswordQuickFolderBreadcrumb("path_$cumulative", part, targetFilter, index == parts.lastIndex)
                    }
                }
            }
        }

        is CategoryFilter.KeePassDatabase -> Unit

        is CategoryFilter.KeePassGroupFilter -> {
            var cumulative = ""
            val parts = filter.groupPath.split("/").filter { it.isNotBlank() }
            parts.forEachIndexed { index, part ->
                cumulative = if (cumulative.isBlank()) part else "$cumulative/$part"
                crumbs += PasswordQuickFolderBreadcrumb(
                    key = "keepass_path_${filter.databaseId}_$cumulative",
                    title = decodeKeePassPathForDisplay(part),
                    targetFilter = CategoryFilter.KeePassGroupFilter(filter.databaseId, cumulative),
                    isCurrent = index == parts.lastIndex
                )
            }
        }

        is CategoryFilter.BitwardenVault -> Unit

        is CategoryFilter.BitwardenFolderFilter -> {
            val folderName = selectedBitwardenFolders.firstOrNull {
                it.bitwardenFolderId.trim() == filter.folderId
            }?.name?.takeIf { it.isNotBlank() }
                ?: categories.firstOrNull {
                    it.bitwardenVaultId == filter.vaultId && it.bitwardenFolderId?.trim() == filter.folderId
                }?.name
                ?: "Folder ${filter.folderId.take(8)}"
            crumbs += PasswordQuickFolderBreadcrumb(
                key = "bitwarden_folder_${filter.vaultId}_${filter.folderId}",
                title = folderName,
                targetFilter = filter,
                isCurrent = true
            )
        }

        else -> Unit
    }

    return crumbs
}

private fun appendRootDatabaseShortcuts(
    shortcuts: MutableList<PasswordQuickFolderShortcut>,
    currentFilter: CategoryFilter,
    quickFolderSourceEntries: List<takagi.ru.monica.data.PasswordEntry>,
    isSearchActive: Boolean,
    keepassDatabases: List<takagi.ru.monica.data.LocalKeePassDatabase>,
    bitwardenVaults: List<takagi.ru.monica.data.bitwarden.BitwardenVault>,
    categories: List<Category>
) {
    if (currentFilter !is CategoryFilter.All) return

    val keepassGroups = quickFolderSourceEntries
        .asSequence()
        .mapNotNull { entry ->
            val databaseId = entry.keepassDatabaseId
            val groupPath = entry.keepassGroupPath?.trim()?.takeIf { it.isNotBlank() }
            if (databaseId != null && groupPath != null) databaseId to groupPath else null
        }
        .groupingBy { it }
        .eachCount()
        .toList()
        .sortedWith(compareBy({ it.first.first }, { it.first.second }))

    keepassGroups.forEach { (key, count) ->
        val databaseId = key.first
        val groupPath = key.second
        val databaseName = keepassDatabases.find { it.id == databaseId }?.name ?: "KeePass"
        shortcuts += PasswordQuickFolderShortcut(
            key = "keepass_${databaseId}_${groupPath}",
            title = decodeKeePassPathForDisplay(groupPath),
            subtitle = "KeePass 组 · $databaseName",
            isBack = false,
            targetFilter = CategoryFilter.KeePassGroupFilter(databaseId, groupPath),
            passwordCount = count
        )
    }

    val linkedFolderNameByKey = categories
        .asSequence()
        .mapNotNull { category ->
            val vaultId = category.bitwardenVaultId
            val folderId = category.bitwardenFolderId?.trim()?.takeIf { it.isNotBlank() }
            if (vaultId != null && folderId != null) (vaultId to folderId) to category.name else null
        }
        .toMap()

    val folderCountByKey = quickFolderSourceEntries
        .asSequence()
        .mapNotNull { entry ->
            val vaultId = entry.bitwardenVaultId
            val folderId = entry.bitwardenFolderId?.trim()?.takeIf { it.isNotBlank() }
            if (vaultId != null && folderId != null) vaultId to folderId else null
        }
        .groupingBy { it }
        .eachCount()
    val knownFolderKeys = if (isSearchActive) {
        folderCountByKey.keys.sortedWith(compareBy({ it.first }, { it.second }))
    } else {
        (folderCountByKey.keys + linkedFolderNameByKey.keys)
            .toSet()
            .sortedWith(compareBy({ it.first }, { it.second }))
    }

    knownFolderKeys.forEach { key ->
        val vaultId = key.first
        val folderId = key.second
        val vaultName = bitwardenVaults.find { it.id == vaultId }?.email ?: "Bitwarden"
        val folderName = linkedFolderNameByKey[key] ?: "Folder ${folderId.take(8)}"
        shortcuts += PasswordQuickFolderShortcut(
            key = "bitwarden_${vaultId}_${folderId}",
            title = folderName,
            subtitle = "Bitwarden 文件夹 · $vaultName",
            isBack = false,
            targetFilter = CategoryFilter.BitwardenFolderFilter(folderId = folderId, vaultId = vaultId),
            passwordCount = folderCountByKey[key] ?: 0
        )
    }
}

internal fun buildKeePassDatabaseQuickFolderShortcuts(
    databaseId: Long,
    keepassGroups: List<KeePassGroupInfo>,
    allPasswords: List<takagi.ru.monica.data.PasswordEntry>,
    isSearchActive: Boolean
): List<PasswordQuickFolderShortcut> {
    val groupNameByPath = LinkedHashMap<String, String>()
    val directChildPaths = linkedSetOf<String>()
    for (group in keepassGroups) {
        val path = group.path.trim()
        if (path.isBlank()) continue
        groupNameByPath[path] = group.name.trim()
        if (path.substringBeforeLast('/', missingDelimiterValue = "").isBlank()) {
            directChildPaths += path
        }
    }
    if (directChildPaths.isEmpty()) return emptyList()

    val subtreeCountByPath = countKeePassSubtreePasswords(databaseId, directChildPaths, allPasswords)
    return directChildPaths.sorted().mapNotNull { childPath ->
        val subtreeCount = subtreeCountByPath[childPath] ?: 0
        if (isSearchActive && subtreeCount <= 0) return@mapNotNull null
        PasswordQuickFolderShortcut(
            key = "keepass_${databaseId}_${childPath}",
            title = groupNameByPath[childPath]?.takeIf { it.isNotBlank() } ?: decodeKeePassPathForDisplay(childPath),
            subtitle = "KeePass 组",
            isBack = false,
            targetFilter = CategoryFilter.KeePassGroupFilter(databaseId, childPath),
            passwordCount = subtreeCount
        )
    }
}

internal fun buildKeePassGroupQuickFolderShortcuts(
    databaseId: Long,
    currentPath: String,
    keepassGroups: List<KeePassGroupInfo>,
    allPasswords: List<takagi.ru.monica.data.PasswordEntry>,
    isSearchActive: Boolean
): List<PasswordQuickFolderShortcut> {
    val groupNameByPath = LinkedHashMap<String, String>()
    val directChildPaths = linkedSetOf<String>()
    for (group in keepassGroups) {
        val path = group.path.trim()
        if (path.isBlank()) continue
        groupNameByPath[path] = group.name.trim()
        val childParent = path.substringBeforeLast('/', missingDelimiterValue = "")
        if (childParent == currentPath) directChildPaths += path
    }
    if (directChildPaths.isEmpty()) return emptyList()

    val subtreeCountByPath = countKeePassSubtreePasswords(databaseId, directChildPaths, allPasswords)
    return directChildPaths.sorted().mapNotNull { childPath ->
        val subtreeCount = subtreeCountByPath[childPath] ?: 0
        if (isSearchActive && subtreeCount <= 0) return@mapNotNull null
        PasswordQuickFolderShortcut(
            key = "keepass_${databaseId}_${childPath}",
            title = groupNameByPath[childPath]?.takeIf { it.isNotBlank() } ?: decodeKeePassPathForDisplay(childPath),
            subtitle = "KeePass 子组",
            isBack = false,
            targetFilter = CategoryFilter.KeePassGroupFilter(databaseId, childPath),
            passwordCount = subtreeCount
        )
    }
}

internal fun countKeePassSubtreePasswords(
    databaseId: Long,
    childPaths: Set<String>,
    allPasswords: List<takagi.ru.monica.data.PasswordEntry>
): Map<String, Int> {
    val counts = childPaths.associateWith { 0 }.toMutableMap()
    if (counts.isEmpty()) return counts

    val sortedPaths = childPaths.sortedByDescending { it.length }
    for (entry in allPasswords) {
        if (entry.keepassDatabaseId != databaseId) continue
        val groupPath = entry.keepassGroupPath?.trim().orEmpty()
        if (groupPath.isBlank()) continue

        val matchPath = sortedPaths.firstOrNull { childPath ->
            groupPath == childPath || groupPath.startsWith("$childPath/")
        } ?: continue
        counts[matchPath] = (counts[matchPath] ?: 0) + 1
    }

    return counts
}

internal fun CategoryFilter.supportsQuickFolders(): Boolean = when (this) {
    is CategoryFilter.All,
    is CategoryFilter.Local,
    is CategoryFilter.Starred,
    is CategoryFilter.Uncategorized,
    is CategoryFilter.LocalStarred,
    is CategoryFilter.LocalUncategorized,
    is CategoryFilter.Custom,
    is CategoryFilter.KeePassDatabase,
    is CategoryFilter.KeePassGroupFilter,
    is CategoryFilter.BitwardenVault,
    is CategoryFilter.BitwardenFolderFilter -> true
    else -> false
}

internal fun CategoryFilter.supportsQuickFolderBreadcrumbs(): Boolean = when (this) {
    is CategoryFilter.KeePassDatabase,
    is CategoryFilter.KeePassGroupFilter,
    is CategoryFilter.BitwardenVault,
    is CategoryFilter.BitwardenFolderFilter -> true
    else -> supportsQuickFolders()
}
