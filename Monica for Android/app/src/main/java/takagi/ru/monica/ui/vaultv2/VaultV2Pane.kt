package takagi.ru.monica.ui.vaultv2

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import takagi.ru.monica.R
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.ui.components.ExpressiveTopBar
import takagi.ru.monica.ui.common.selection.SelectionActionBar
import takagi.ru.monica.viewmodel.PasswordViewModel
import takagi.ru.monica.viewmodel.TotpViewModel

private enum class VaultV2Filter {
	ALL,
	PASSWORD,
	AUTHENTICATOR,
}

private enum class VaultV2ItemType {
	PASSWORD,
	AUTHENTICATOR,
}

private data class VaultV2Item(
	val key: String,
	val type: VaultV2ItemType,
	val title: String,
	val subtitle: String,
	val isFavorite: Boolean,
	val sortKey: String,
	val passwordEntry: PasswordEntry? = null,
	val totpItem: SecureItem? = null,
	val boundPasswordId: Long? = null,
)

@OptIn(
	ExperimentalMaterial3Api::class,
	ExperimentalFoundationApi::class,
)
@Composable
fun VaultV2Pane(
	passwordViewModel: PasswordViewModel,
	totpViewModel: TotpViewModel,
	onOpenPassword: (Long) -> Unit,
	onOpenTotp: (Long) -> Unit,
	onBackToTopVisibilityChange: (Boolean) -> Unit = {},
	scrollToTopRequestKey: Int = 0,
	modifier: Modifier = Modifier,
) {
	var searchQuery by rememberSaveable { mutableStateOf("") }
	var isSearchExpanded by rememberSaveable { mutableStateOf(false) }
	var filter by rememberSaveable { mutableStateOf(VaultV2Filter.ALL) }
	val selectedKeys = remember { mutableStateListOf<String>() }
	val listState = rememberLazyListState()

	val passwordEntries by passwordViewModel.passwordEntries.collectAsState()
	val totpItems by totpViewModel.totpItems.collectAsState()

	val allItemsRaw = remember(passwordEntries, totpItems) {
		val passwordList = passwordEntries.map { entry ->
			VaultV2Item(
				key = "password:${entry.id}",
				type = VaultV2ItemType.PASSWORD,
				title = entry.title.ifBlank { "(Untitled)" },
				subtitle = entry.username.ifBlank { entry.website }.ifBlank { "-" },
				isFavorite = entry.isFavorite,
				sortKey = entry.title.ifBlank { "#" },
				passwordEntry = entry,
			)
		}

		val totpList = totpItems.map { item ->
			val data = runCatching { Json.decodeFromString<TotpData>(item.itemData) }.getOrNull()
			val subtitle = listOf(data?.issuer, data?.accountName)
				.filterNotNull()
				.map { it.trim() }
				.filter { it.isNotEmpty() }
				.joinToString(" · ")
				.ifBlank { item.notes.ifBlank { "-" } }

			VaultV2Item(
				key = "totp:${item.id}",
				type = VaultV2ItemType.AUTHENTICATOR,
				title = item.title.ifBlank { data?.issuer ?: "(Untitled)" },
				subtitle = subtitle,
				isFavorite = item.isFavorite,
				sortKey = item.title.ifBlank { data?.issuer ?: "#" },
				totpItem = item,
				boundPasswordId = data?.boundPasswordId,
			)
		}

		(passwordList + totpList)
			.sortedWith(
				compareBy<VaultV2Item> { it.sortKey.lowercase() }
					.thenBy { it.type.ordinal }
					.thenBy { it.key }
			)
	}

	var allItems by remember { mutableStateOf(allItemsRaw) }
	var pendingAllItems by remember { mutableStateOf<List<VaultV2Item>?>(null) }
	var isAutoScrollingToTop by remember { mutableStateOf(false) }
	LaunchedEffect(allItemsRaw) {
		if (isAutoScrollingToTop) {
			pendingAllItems = allItemsRaw
		} else {
			// Coalesce rapid multi-source emissions so the list appears in one stable batch.
			delay(120)
			allItems = allItemsRaw
		}
	}

	LaunchedEffect(isAutoScrollingToTop) {
		if (!isAutoScrollingToTop) {
			pendingAllItems?.let { buffered ->
				allItems = buffered
				pendingAllItems = null
			}
		}
	}

	val normalizedQuery = remember(searchQuery) { searchQuery.trim() }
	val filteredItems = remember(allItems, filter, normalizedQuery) {
		allItems.filter { item ->
			val matchesFilter = when (filter) {
				VaultV2Filter.ALL -> true
				VaultV2Filter.PASSWORD -> item.type == VaultV2ItemType.PASSWORD
				VaultV2Filter.AUTHENTICATOR -> item.type == VaultV2ItemType.AUTHENTICATOR
			}
			if (!matchesFilter) return@filter false

			if (normalizedQuery.isBlank()) {
				true
			} else {
				item.title.contains(normalizedQuery, ignoreCase = true) ||
					item.subtitle.contains(normalizedQuery, ignoreCase = true)
			}
		}
	}

	val groupedItems = remember(filteredItems) {
		filteredItems.groupBy { item ->
			firstLetterGroup(item.sortKey)
		}
	}

	val sectionedItems = remember(groupedItems) {
		groupedItems.keys
			.sortedWith(compareBy<String> { if (it == "#") 1 else 0 }.thenBy { it })
			.map { section -> section to groupedItems[section].orEmpty() }
	}

	val selectedCount by remember { derivedStateOf { selectedKeys.size } }
	val selectedItems = remember(selectedKeys, allItems) {
		val keySet = selectedKeys.toSet()
		allItems.filter { it.key in keySet }
	}
	val showBackToTop by remember(listState) {
		derivedStateOf { listState.firstVisibleItemIndex > 3 }
	}

	LaunchedEffect(showBackToTop) {
		onBackToTopVisibilityChange(showBackToTop)
	}

	LaunchedEffect(scrollToTopRequestKey) {
		if (scrollToTopRequestKey > 0) {
			isAutoScrollingToTop = true
			try {
				listState.animateScrollToItem(0)
			} finally {
				isAutoScrollingToTop = false
			}
		}
	}

	Box(modifier = modifier.fillMaxSize()) {
		Column(modifier = Modifier.fillMaxSize()) {
			ExpressiveTopBar(
				title = stringResource(R.string.nav_v2_vault),
				searchQuery = searchQuery,
				onSearchQueryChange = { searchQuery = it },
				isSearchExpanded = isSearchExpanded,
				onSearchExpandedChange = { isSearchExpanded = it },
				searchHint = stringResource(R.string.topbar_search_hint),
				actions = {
					IconButton(onClick = { isSearchExpanded = true }) {
						Icon(
							imageVector = Icons.Default.Search,
							contentDescription = stringResource(R.string.search),
						)
					}
				}
			)

			VaultV2FilterRow(
				current = filter,
				onChange = { filter = it },
			)

			VaultV2List(
				sections = sectionedItems,
				listState = listState,
				selectedKeys = selectedKeys,
				onOpenItem = { item ->
					when (item.type) {
						VaultV2ItemType.PASSWORD -> item.passwordEntry?.id?.let(onOpenPassword)
						VaultV2ItemType.AUTHENTICATOR -> {
							val totp = item.totpItem ?: return@VaultV2List
							if (totp.id > 0) {
								onOpenTotp(totp.id)
							} else {
								item.boundPasswordId?.let(onOpenPassword)
							}
						}
					}
				},
			)
		}

		if (selectedCount > 0) {
			SelectionActionBar(
				modifier = Modifier
					.align(Alignment.BottomCenter)
					.padding(bottom = 20.dp),
				selectedCount = selectedCount,
				onExit = { selectedKeys.clear() },
				onSelectAll = {
					selectedKeys.clear()
					selectedKeys.addAll(filteredItems.map { it.key })
				},
				onFavorite = {
					selectedItems.forEach { item ->
						when (item.type) {
							VaultV2ItemType.PASSWORD -> {
								item.passwordEntry?.let { entry ->
									passwordViewModel.toggleFavorite(entry.id, !entry.isFavorite)
								}
							}

							VaultV2ItemType.AUTHENTICATOR -> {
								val id = item.totpItem?.id ?: return@forEach
								if (id > 0) {
									totpViewModel.toggleFavorite(id, !item.isFavorite)
								}
							}
						}
					}
					selectedKeys.clear()
				},
				onDelete = {
					selectedItems.forEach { item ->
						when (item.type) {
							VaultV2ItemType.PASSWORD -> {
								item.passwordEntry?.let(passwordViewModel::deletePasswordEntry)
							}

							VaultV2ItemType.AUTHENTICATOR -> {
								item.totpItem?.let { totp ->
									if (totp.id > 0) {
										totpViewModel.deleteTotpItem(totp)
									}
								}
							}
						}
					}
					selectedKeys.clear()
				},
			)
		}

	}
}

@Composable
private fun VaultV2FilterRow(
	current: VaultV2Filter,
	onChange: (VaultV2Filter) -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp, vertical = 6.dp),
		horizontalArrangement = Arrangement.spacedBy(8.dp),
	) {
		VaultV2FilterChip(
			text = stringResource(R.string.filter_all),
			selected = current == VaultV2Filter.ALL,
			onClick = { onChange(VaultV2Filter.ALL) },
		)
		VaultV2FilterChip(
			text = stringResource(R.string.nav_passwords),
			selected = current == VaultV2Filter.PASSWORD,
			onClick = { onChange(VaultV2Filter.PASSWORD) },
		)
		VaultV2FilterChip(
			text = stringResource(R.string.nav_authenticator),
			selected = current == VaultV2Filter.AUTHENTICATOR,
			onClick = { onChange(VaultV2Filter.AUTHENTICATOR) },
		)
	}
}

@Composable
private fun VaultV2FilterChip(
	text: String,
	selected: Boolean,
	onClick: () -> Unit,
) {
	AssistChip(
		onClick = onClick,
		label = {
			Text(text = text)
		},
		colors = AssistChipDefaults.assistChipColors(
			containerColor = if (selected) {
				MaterialTheme.colorScheme.primaryContainer
			} else {
				MaterialTheme.colorScheme.surfaceContainerLow
			},
			labelColor = if (selected) {
				MaterialTheme.colorScheme.onPrimaryContainer
			} else {
				MaterialTheme.colorScheme.onSurfaceVariant
			},
		),
	)
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun VaultV2List(
	sections: List<Pair<String, List<VaultV2Item>>>,
	listState: LazyListState,
	selectedKeys: MutableList<String>,
	onOpenItem: (VaultV2Item) -> Unit,
) {
	LazyColumn(
		state = listState,
		modifier = Modifier.fillMaxSize(),
		contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
		verticalArrangement = Arrangement.spacedBy(6.dp),
	) {
		if (sections.isEmpty()) {
			item(key = "empty") {
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 56.dp),
					contentAlignment = Alignment.Center,
				) {
					Text(
						text = stringResource(R.string.no_results),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
			}
		}

		sections.forEach { (section, itemsInSection) ->
			item(key = "header:$section") {
				Surface(color = MaterialTheme.colorScheme.surface) {
					Text(
						text = section,
						style = MaterialTheme.typography.titleSmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier
							.fillMaxWidth()
							.padding(horizontal = 8.dp, vertical = 4.dp),
					)
				}
			}

			items(itemsInSection, key = { item -> item.key }) { item ->
				val selected = item.key in selectedKeys
				VaultV2ItemCard(
					item = item,
					selected = selected,
					onClick = {
						if (selectedKeys.isNotEmpty()) {
							if (selected) {
								selectedKeys.remove(item.key)
							} else {
								selectedKeys.add(item.key)
							}
						} else {
							onOpenItem(item)
						}
					},
					onLongClick = {
						if (selected) {
							selectedKeys.remove(item.key)
						} else {
							selectedKeys.add(item.key)
						}
					}
				)
			}
		}
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaultV2ItemCard(
	item: VaultV2Item,
	selected: Boolean,
	onClick: () -> Unit,
	onLongClick: () -> Unit,
) {
	val icon = when (item.type) {
		VaultV2ItemType.PASSWORD -> Icons.Default.Lock
		VaultV2ItemType.AUTHENTICATOR -> Icons.Default.Security
	}

	Surface(
		shape = RoundedCornerShape(14.dp),
		color = if (selected) {
			MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
		} else {
			MaterialTheme.colorScheme.surfaceContainerLow
		},
		modifier = Modifier
			.fillMaxWidth()
			.combinedClickable(onClick = onClick, onLongClick = onLongClick),
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 10.dp, vertical = 8.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(10.dp),
		) {
			Surface(
				shape = RoundedCornerShape(10.dp),
				color = MaterialTheme.colorScheme.primaryContainer,
			) {
				Icon(
					imageVector = icon,
					contentDescription = null,
					tint = MaterialTheme.colorScheme.onPrimaryContainer,
					modifier = Modifier
						.padding(8.dp)
						.size(16.dp),
				)
			}

			Column(
				modifier = Modifier.weight(1f),
				verticalArrangement = Arrangement.spacedBy(2.dp),
			) {
				Text(
					text = item.title,
					style = MaterialTheme.typography.titleSmall,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				Text(
					text = item.subtitle,
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}

			if (item.isFavorite) {
				Icon(
					imageVector = Icons.Outlined.Favorite,
					contentDescription = null,
					tint = MaterialTheme.colorScheme.primary,
					modifier = Modifier.size(16.dp),
				)
				Spacer(modifier = Modifier.width(2.dp))
			}

			Icon(
				imageVector = Icons.AutoMirrored.Filled.ArrowRight,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.size(18.dp),
			)
		}
	}
}

private fun firstLetterGroup(raw: String): String {
	val first = raw.trim().firstOrNull()?.uppercaseChar() ?: return "#"
	return if (first in 'A'..'Z') first.toString() else "#"
}

