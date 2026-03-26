package takagi.ru.monica.ui.vaultv2

import android.icu.text.Transliterator
import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.serialization.json.Json
import takagi.ru.monica.R
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.isLocalOnlyItem
import takagi.ru.monica.data.isLocalOnlyPasskey
import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.model.BankCardData
import takagi.ru.monica.data.model.DocumentData
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.data.model.OtpType
import takagi.ru.monica.data.UnmatchedIconHandlingStrategy
import takagi.ru.monica.notes.domain.NoteContentCodec
import takagi.ru.monica.ui.icons.PASSWORD_ICON_TYPE_NONE
import takagi.ru.monica.ui.icons.PASSWORD_ICON_TYPE_SIMPLE
import takagi.ru.monica.ui.icons.PASSWORD_ICON_TYPE_UPLOADED
import takagi.ru.monica.ui.icons.UnmatchedIconFallback
import takagi.ru.monica.ui.common.pull.rememberPullActionState
import takagi.ru.monica.ui.icons.rememberAutoMatchedSimpleIcon
import takagi.ru.monica.ui.icons.rememberSimpleIconBitmap
import takagi.ru.monica.ui.icons.rememberUploadedPasswordIcon
import takagi.ru.monica.ui.icons.shouldShowFallbackSlot
import takagi.ru.monica.ui.components.ExpressiveTopBar
import takagi.ru.monica.ui.common.selection.SelectionActionBar
import takagi.ru.monica.ui.password.PasswordAuthenticatorDisplayState
import takagi.ru.monica.ui.password.rememberPasswordAuthenticatorDisplayState
import takagi.ru.monica.viewmodel.BankCardViewModel
import takagi.ru.monica.viewmodel.DocumentViewModel
import takagi.ru.monica.viewmodel.NoteViewModel
import takagi.ru.monica.viewmodel.PasskeyViewModel
import takagi.ru.monica.viewmodel.PasswordViewModel
import takagi.ru.monica.viewmodel.TotpViewModel
import takagi.ru.monica.util.TotpGenerator
import java.util.concurrent.CancellationException
import java.util.Locale
import kotlin.math.roundToInt

private enum class VaultV2Filter {
	ALL,
	PASSWORD,
	AUTHENTICATOR,
	NOTE,
	PASSKEY,
	CARD_WALLET,
}

private enum class VaultV2ItemType {
	PASSWORD,
	AUTHENTICATOR,
	NOTE,
	PASSKEY,
	BANK_CARD,
	DOCUMENT,
}

private data class VaultV2Item(
	val key: String,
	val type: VaultV2ItemType,
	val title: String,
	val subtitle: String,
	val isFavorite: Boolean,
	val sortKey: String,
	val searchText: String,
	val passwordEntry: PasswordEntry? = null,
	val totpItem: SecureItem? = null,
	val secureItem: SecureItem? = null,
	val passkeyEntry: PasskeyEntry? = null,
	val boundPasswordId: Long? = null,
)

private data class VaultV2SectionLayout(
	val title: String,
	val items: List<VaultV2Item>,
	val itemStartIndex: Int,
	val firstItemLazyIndex: Int,
)

private const val VAULT_V2_FAST_SCROLL_LOG_TAG = "VaultV2FastScroll"
private val vaultV2Transliterator: Transliterator by lazy(LazyThreadSafetyMode.NONE) {
	Transliterator.getInstance("Any-Latin; Latin-ASCII")
}

private fun PasswordEntry.isVaultV2LocalOnly(): Boolean {
	return isLocalOnlyEntry()
}

private fun SecureItem.isVaultV2LocalOnly(): Boolean {
	return isLocalOnlyItem()
}

private fun PasskeyEntry.isVaultV2LocalOnly(): Boolean {
	return isLocalOnlyPasskey()
}

@OptIn(
	ExperimentalMaterial3Api::class,
	ExperimentalFoundationApi::class,
)
@Composable
fun VaultV2Pane(
	passwordViewModel: PasswordViewModel,
	totpViewModel: TotpViewModel,
	bankCardViewModel: BankCardViewModel,
	documentViewModel: DocumentViewModel,
	noteViewModel: NoteViewModel,
	passkeyViewModel: PasskeyViewModel,
	onOpenPassword: (Long) -> Unit,
	onOpenTotp: (Long) -> Unit,
	onOpenBankCard: (Long) -> Unit,
	onOpenDocument: (Long) -> Unit,
	onOpenNote: (Long) -> Unit,
	onOpenPasskey: (PasskeyEntry) -> Unit,
	onBackToTopVisibilityChange: (Boolean) -> Unit = {},
	onFastScrollSectionLabelChange: (String?) -> Unit = {},
	scrollToTopRequestKey: Int = 0,
	showOnlyLocalData: Boolean = true,
	appSettings: AppSettings = AppSettings(),
	modifier: Modifier = Modifier,
) {
	var searchQuery by rememberSaveable { mutableStateOf("") }
	var isSearchExpanded by rememberSaveable { mutableStateOf(false) }
	var filter by rememberSaveable { mutableStateOf(VaultV2Filter.ALL) }
	val selectedKeys = remember { mutableStateListOf<String>() }
	val listState = rememberLazyListState()
	val context = LocalContext.current
	val density = LocalDensity.current
	val scope = rememberCoroutineScope()
	val bitwardenRepository = remember(context) {
		takagi.ru.monica.bitwarden.repository.BitwardenRepository.getInstance(context)
	}
	val pullSearchTriggerDistance = remember(density) { with(density) { 40.dp.toPx() } }
	val pullSyncTriggerDistance = remember(density) { with(density) { 72.dp.toPx() } }
	val pullMaxDragDistance = remember(density) { with(density) { 100.dp.toPx() } }
	val pullAction = rememberPullActionState(
		isBitwardenDatabaseView = false,
		isSearchExpanded = isSearchExpanded,
		searchTriggerDistance = pullSearchTriggerDistance,
		syncTriggerDistance = pullSyncTriggerDistance,
		maxDragDistance = pullMaxDragDistance,
		bitwardenRepository = bitwardenRepository,
		onSearchTriggered = { isSearchExpanded = true },
	)

	val passwordEntries by passwordViewModel.passwordEntries.collectAsState()
	val totpItems by totpViewModel.totpItems.collectAsState()
	val bankCardItems by bankCardViewModel.allCards.collectAsState(initial = emptyList())
	val documentItems by documentViewModel.allDocuments.collectAsState(initial = emptyList())
	val noteItems by noteViewModel.allNotes.collectAsState(initial = emptyList())
	val passkeyItems by passkeyViewModel.allPasskeys.collectAsState()
	val fastScrollRequestKey by passwordViewModel.fastScrollRequestKey.collectAsState()
	val fastScrollProgress by passwordViewModel.fastScrollProgress.collectAsState()

	val visiblePasswordEntries = remember(passwordEntries, showOnlyLocalData) {
		if (showOnlyLocalData) passwordEntries.filter { it.isVaultV2LocalOnly() } else passwordEntries
	}
	val visibleTotpItems = remember(totpItems, showOnlyLocalData) {
		if (showOnlyLocalData) totpItems.filter { it.isVaultV2LocalOnly() } else totpItems
	}
	val visibleBankCardItems = remember(bankCardItems, showOnlyLocalData) {
		if (showOnlyLocalData) bankCardItems.filter { it.isVaultV2LocalOnly() } else bankCardItems
	}
	val visibleDocumentItems = remember(documentItems, showOnlyLocalData) {
		if (showOnlyLocalData) documentItems.filter { it.isVaultV2LocalOnly() } else documentItems
	}
	val visibleNoteItems = remember(noteItems, showOnlyLocalData) {
		if (showOnlyLocalData) noteItems.filter { it.isVaultV2LocalOnly() } else noteItems
	}
	val visiblePasskeyItems = remember(passkeyItems, showOnlyLocalData) {
		if (showOnlyLocalData) passkeyItems.filter { it.isVaultV2LocalOnly() } else passkeyItems
	}

	val allItemsRaw = remember(
		visiblePasswordEntries,
		visibleTotpItems,
		visibleBankCardItems,
		visibleDocumentItems,
		visibleNoteItems,
		visiblePasskeyItems,
	) {
		val passwordList = visiblePasswordEntries.map { entry ->
			val displayTitle = entry.title.ifBlank { "(Untitled)" }
			val subtitle = entry.username.ifBlank { entry.website }.ifBlank { "-" }
			VaultV2Item(
				key = "password:${entry.id}",
				type = VaultV2ItemType.PASSWORD,
				title = displayTitle,
				subtitle = subtitle,
				isFavorite = entry.isFavorite,
				sortKey = normalizedVaultV2SortKey(displayTitle),
				searchText = listOf(displayTitle, entry.username, entry.website, entry.appName, entry.notes)
					.filter { it.isNotBlank() }
					.joinToString("\n"),
				passwordEntry = entry,
			)
		}

		val totpList = visibleTotpItems.map { item ->
			val data = runCatching { Json.decodeFromString<TotpData>(item.itemData) }.getOrNull()
			val subtitle = listOf(data?.issuer, data?.accountName)
				.filterNotNull()
				.map { it.trim() }
				.filter { it.isNotEmpty() }
				.joinToString(" · ")
				.ifBlank { item.notes.ifBlank { "-" } }

			val displayTitle = item.title.ifBlank { data?.issuer ?: "(Untitled)" }
			VaultV2Item(
				key = "totp:${item.id}",
				type = VaultV2ItemType.AUTHENTICATOR,
				title = displayTitle,
				subtitle = subtitle,
				isFavorite = item.isFavorite,
				sortKey = normalizedVaultV2SortKey(displayTitle),
				searchText = listOf(displayTitle, subtitle, item.notes, item.itemData)
					.filter { it.isNotBlank() }
					.joinToString("\n"),
				totpItem = item,
				boundPasswordId = data?.boundPasswordId,
			)
		}

		val noteList = visibleNoteItems.map { item ->
			val displayTitle = item.title.ifBlank { "(Untitled)" }
			val decoded = NoteContentCodec.decodeFromItem(item)
			val previewText = vaultV2PlainSingleLine(
				NoteContentCodec.toPlainPreview(decoded.content, decoded.isMarkdown)
			)
			VaultV2Item(
				key = "note:${item.id}",
				type = VaultV2ItemType.NOTE,
				title = displayTitle,
				subtitle = previewText.ifBlank { item.notes.ifBlank { "-" } },
				isFavorite = item.isFavorite,
				sortKey = normalizedVaultV2SortKey(displayTitle),
				searchText = listOf(displayTitle, decoded.content, decoded.tags.joinToString(" "), item.notes)
					.filter { it.isNotBlank() }
					.joinToString("\n"),
				secureItem = item,
			)
		}

		val passkeyList = visiblePasskeyItems.map { passkey ->
			val displayTitle = passkey.rpName.ifBlank { passkey.rpId }.ifBlank { "(Untitled)" }
			val subtitle = listOf(
				passkey.userDisplayName.ifBlank { passkey.userName },
				passkey.userName.takeIf {
					it.isNotBlank() && it != passkey.userDisplayName
				},
				passkey.rpId.takeIf { it.isNotBlank() && it != displayTitle }
			)
				.filterNotNull()
				.filter { it.isNotBlank() }
				.joinToString(" · ")
				.ifBlank { "-" }
			VaultV2Item(
				key = "passkey:${passkey.credentialId}",
				type = VaultV2ItemType.PASSKEY,
				title = displayTitle,
				subtitle = subtitle,
				isFavorite = false,
				sortKey = normalizedVaultV2SortKey(displayTitle),
				searchText = listOf(
					displayTitle,
					passkey.rpId,
					passkey.userName,
					passkey.userDisplayName,
					passkey.notes,
				).filter { it.isNotBlank() }.joinToString("\n"),
				passkeyEntry = passkey,
				boundPasswordId = passkey.boundPasswordId,
			)
		}

		val bankCardList = visibleBankCardItems.map { item ->
			val data = runCatching { Json.decodeFromString<BankCardData>(item.itemData) }.getOrNull()
			val displayTitle = item.title.ifBlank { data?.bankName ?: "(Untitled)" }
			val subtitle = vaultV2BankCardSubtitle(data = data, fallbackNotes = item.notes)
			VaultV2Item(
				key = "bank_card:${item.id}",
				type = VaultV2ItemType.BANK_CARD,
				title = displayTitle,
				subtitle = subtitle,
				isFavorite = item.isFavorite,
				sortKey = normalizedVaultV2SortKey(displayTitle),
				searchText = listOf(
					displayTitle,
					data?.bankName.orEmpty(),
					data?.cardholderName.orEmpty(),
					data?.cardNumber.orEmpty(),
					item.notes,
				).filter { it.isNotBlank() }.joinToString("\n"),
				secureItem = item,
			)
		}

		val documentList = visibleDocumentItems.map { item ->
			val data = runCatching { Json.decodeFromString<DocumentData>(item.itemData) }.getOrNull()
			val displayTitle = item.title.ifBlank { data?.fullName ?: "(Untitled)" }
			val subtitle = vaultV2DocumentSubtitle(data = data, fallbackNotes = item.notes)
			VaultV2Item(
				key = "document:${item.id}",
				type = VaultV2ItemType.DOCUMENT,
				title = displayTitle,
				subtitle = subtitle,
				isFavorite = item.isFavorite,
				sortKey = normalizedVaultV2SortKey(displayTitle),
				searchText = listOf(
					displayTitle,
					data?.fullName.orEmpty(),
					data?.documentNumber.orEmpty(),
					data?.issuedBy.orEmpty(),
					item.notes,
				).filter { it.isNotBlank() }.joinToString("\n"),
				secureItem = item,
			)
		}

		dedupeExactVaultItems(passwordList + totpList + noteList + passkeyList + bankCardList + documentList)
			.sortedWith(
				compareBy<VaultV2Item> { it.sortKey.lowercase(Locale.ROOT) }
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
				VaultV2Filter.NOTE -> item.type == VaultV2ItemType.NOTE
				VaultV2Filter.PASSKEY -> item.type == VaultV2ItemType.PASSKEY
				VaultV2Filter.CARD_WALLET -> {
					item.type == VaultV2ItemType.BANK_CARD || item.type == VaultV2ItemType.DOCUMENT
				}
			}
			if (!matchesFilter) return@filter false

			if (normalizedQuery.isBlank()) {
				true
			} else {
				item.searchText.contains(normalizedQuery, ignoreCase = true)
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
	val sectionLayouts = remember(sectionedItems) {
		var itemStartIndex = 0
		var lazyIndex = 0
		sectionedItems.map { (sectionTitle, itemsInSection) ->
			VaultV2SectionLayout(
				title = sectionTitle,
				items = itemsInSection,
				itemStartIndex = itemStartIndex,
				firstItemLazyIndex = lazyIndex + 1,
			).also {
				itemStartIndex += itemsInSection.size
				lazyIndex += itemsInSection.size + 1
			}
		}
	}

	val selectedCount by remember { derivedStateOf { selectedKeys.size } }
	val selectedItems = remember(selectedKeys, allItems) {
		val keySet = selectedKeys.toSet()
		allItems.filter { it.key in keySet }
	}
	val currentSectionIndicatorLabel by remember(listState, sectionLayouts) {
		derivedStateOf {
			if (sectionLayouts.isEmpty()) {
				"#"
			} else {
				vaultV2SectionTitleForLazyIndex(
					sectionLayouts = sectionLayouts,
					lazyIndex = listState.firstVisibleItemIndex,
				)?.take(2)?.uppercase(Locale.ROOT) ?: sectionLayouts.first().title
			}
		}
	}
	val passwordById = remember(passwordEntries) {
		passwordEntries.associateBy { it.id }
	}
	val showBackToTop by remember(listState) {
		derivedStateOf { listState.firstVisibleItemIndex > 3 }
	}

	LaunchedEffect(showBackToTop) {
		onBackToTopVisibilityChange(showBackToTop)
	}

	DisposableEffect(Unit) {
		onDispose {
			onFastScrollSectionLabelChange(null)
		}
	}

	LaunchedEffect(scrollToTopRequestKey) {
		if (scrollToTopRequestKey > 0) {
			isAutoScrollingToTop = true
			try {
				runCatching {
					listState.animateScrollToItem(0)
				}
				listState.scrollToItem(0)
			} finally {
				isAutoScrollingToTop = false
			}
		}
	}

	LaunchedEffect(listState, sectionLayouts, filteredItems.size) {
		snapshotFlow {
			if (fastScrollRequestKey <= 0 || filteredItems.isEmpty() || sectionLayouts.isEmpty()) {
				null
			} else {
				val targetItemIndex = (
					fastScrollProgress.coerceIn(0f, 1f) * (filteredItems.size - 1)
				).roundToInt().coerceIn(0, filteredItems.size - 1)
				vaultV2LazyIndexForItemIndex(
					sectionLayouts = sectionLayouts,
					targetItemIndex = targetItemIndex,
				)
			}
		}
			.filterNotNull()
			.distinctUntilChanged()
			.conflate()
			.collectLatest { targetLazyIndex ->
				if (listState.firstVisibleItemIndex == targetLazyIndex) return@collectLatest
				runCatching {
					listState.scrollToItem(index = targetLazyIndex)
				}.onFailure { throwable ->
					if (throwable is CancellationException) return@onFailure
					Log.e(
						VAULT_V2_FAST_SCROLL_LOG_TAG,
						"scrollToItem failed: targetLazyIndex=$targetLazyIndex filteredSize=${filteredItems.size}",
						throwable
					)
				}
			}
	}

	LaunchedEffect(listState, sectionLayouts, filteredItems.size) {
		snapshotFlow {
			if (filteredItems.size <= 1 || sectionLayouts.isEmpty()) {
				0f
			} else {
				val currentItemIndex = vaultV2ItemIndexForLazyIndex(
					sectionLayouts = sectionLayouts,
					lazyIndex = listState.firstVisibleItemIndex,
				).coerceIn(0, filteredItems.size - 1)
				(currentItemIndex.toFloat() / (filteredItems.size - 1).toFloat()).coerceIn(0f, 1f)
			}
		}
			.distinctUntilChanged()
			.collect { progress ->
				passwordViewModel.updateFastScrollProgress(progress)
			}
	}

	LaunchedEffect(listState, sectionLayouts, filteredItems.size) {
		snapshotFlow {
			if (filteredItems.isEmpty() || sectionLayouts.isEmpty()) {
				null
			} else {
				vaultV2SectionTitleForLazyIndex(
					sectionLayouts = sectionLayouts,
					lazyIndex = listState.firstVisibleItemIndex,
				)
			}
		}
			.distinctUntilChanged()
			.collect { sectionTitle ->
				onFastScrollSectionLabelChange(sectionTitle)
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

			VaultV2PathBanner(
				pathLabel = stringResource(R.string.filter_monica),
				currentSectionLabel = currentSectionIndicatorLabel,
			)

			val contentPullOffset = pullAction.currentOffset.toInt()
			val listInteractionModifier = Modifier
				.offset { IntOffset(x = 0, y = contentPullOffset) }
				.nestedScroll(pullAction.nestedScrollConnection)
				.then(
					if (sectionedItems.isEmpty()) {
						Modifier.pointerInput(Unit) {
							detectVerticalDragGestures(
								onVerticalDrag = { _, dragAmount ->
									pullAction.onVerticalDrag(dragAmount)
								},
								onDragEnd = pullAction.onDragEnd,
								onDragCancel = pullAction.onDragCancel,
							)
						}
					} else {
						Modifier
					}
				)

			VaultV2List(
				currentFilter = filter,
				onFilterChange = { filter = it },
				sections = sectionedItems,
				listState = listState,
				passwordById = passwordById,
				appSettings = appSettings,
				selectedKeys = selectedKeys,
				modifier = Modifier
					.weight(1f)
					.fillMaxWidth()
					.then(listInteractionModifier),
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
						VaultV2ItemType.NOTE -> item.secureItem?.id?.let(onOpenNote)
						VaultV2ItemType.PASSKEY -> item.passkeyEntry?.let(onOpenPasskey)
						VaultV2ItemType.BANK_CARD -> item.secureItem?.id?.let(onOpenBankCard)
						VaultV2ItemType.DOCUMENT -> item.secureItem?.id?.let(onOpenDocument)
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

							VaultV2ItemType.NOTE -> {
								item.secureItem?.let { note ->
									val decoded = NoteContentCodec.decodeFromItem(note)
									noteViewModel.updateNote(
										id = note.id,
										content = decoded.content,
										title = note.title,
										tags = decoded.tags,
										isMarkdown = decoded.isMarkdown,
										isFavorite = !note.isFavorite,
										createdAt = note.createdAt,
										categoryId = note.categoryId,
										imagePaths = note.imagePaths,
										keepassDatabaseId = note.keepassDatabaseId,
										keepassGroupPath = note.keepassGroupPath,
										bitwardenVaultId = note.bitwardenVaultId,
										bitwardenFolderId = note.bitwardenFolderId,
									)
								}
							}

							VaultV2ItemType.PASSKEY -> Unit

							VaultV2ItemType.BANK_CARD -> {
								item.secureItem?.id?.let(bankCardViewModel::toggleFavorite)
							}

							VaultV2ItemType.DOCUMENT -> {
								item.secureItem?.id?.let(documentViewModel::toggleFavorite)
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

							VaultV2ItemType.NOTE -> {
								item.secureItem?.let(noteViewModel::deleteNote)
							}

							VaultV2ItemType.PASSKEY -> {
								item.passkeyEntry?.let { passkey ->
									scope.launch {
										passkeyViewModel.deletePasskey(passkey)
									}
								}
							}

							VaultV2ItemType.BANK_CARD -> {
								item.secureItem?.id?.let(bankCardViewModel::deleteCard)
							}

							VaultV2ItemType.DOCUMENT -> {
								item.secureItem?.id?.let(documentViewModel::deleteDocument)
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
	LazyRow(
		modifier = Modifier
			.fillMaxWidth()
			.padding(vertical = 6.dp),
		contentPadding = PaddingValues(horizontal = 0.dp),
		horizontalArrangement = Arrangement.spacedBy(8.dp),
	) {
		item {
			VaultV2FilterChip(
				text = stringResource(R.string.filter_all),
				selected = current == VaultV2Filter.ALL,
				onClick = { onChange(VaultV2Filter.ALL) },
			)
		}
		item {
			VaultV2FilterChip(
				text = stringResource(R.string.nav_passwords),
				selected = current == VaultV2Filter.PASSWORD,
				onClick = { onChange(VaultV2Filter.PASSWORD) },
			)
		}
		item {
			VaultV2FilterChip(
				text = stringResource(R.string.nav_authenticator),
				selected = current == VaultV2Filter.AUTHENTICATOR,
				onClick = { onChange(VaultV2Filter.AUTHENTICATOR) },
			)
		}
		item {
			VaultV2FilterChip(
				text = stringResource(R.string.nav_notes),
				selected = current == VaultV2Filter.NOTE,
				onClick = { onChange(VaultV2Filter.NOTE) },
			)
		}
		item {
			VaultV2FilterChip(
				text = stringResource(R.string.nav_passkey),
				selected = current == VaultV2Filter.PASSKEY,
				onClick = { onChange(VaultV2Filter.PASSKEY) },
			)
		}
		item {
			VaultV2FilterChip(
				text = stringResource(R.string.nav_card_wallet),
				selected = current == VaultV2Filter.CARD_WALLET,
				onClick = { onChange(VaultV2Filter.CARD_WALLET) },
			)
		}
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
	currentFilter: VaultV2Filter,
	onFilterChange: (VaultV2Filter) -> Unit,
	sections: List<Pair<String, List<VaultV2Item>>>,
	listState: LazyListState,
	passwordById: Map<Long, PasswordEntry>,
	appSettings: AppSettings,
	selectedKeys: MutableList<String>,
	modifier: Modifier = Modifier,
	onOpenItem: (VaultV2Item) -> Unit,
) {
	LazyColumn(
		state = listState,
		modifier = modifier,
		contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
	verticalArrangement = Arrangement.spacedBy(6.dp),
	) {
		item(key = "filter_row") {
			VaultV2FilterRow(
				current = currentFilter,
				onChange = onFilterChange,
			)
		}

		if (sections.isEmpty()) {
			item(key = "empty") {
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 40.dp),
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
							.padding(horizontal = 12.dp, vertical = 4.dp),
					)
				}
			}

			items(itemsInSection, key = { item -> item.key }) { item ->
				val selected = item.key in selectedKeys
				VaultV2ItemCard(
					item = item,
					boundPassword = when (item.type) {
						VaultV2ItemType.AUTHENTICATOR,
						VaultV2ItemType.PASSKEY -> item.boundPasswordId?.let(passwordById::get)
						else -> null
					},
					appSettings = appSettings,
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

@Composable
private fun VaultV2PathBanner(
	pathLabel: String,
	currentSectionLabel: String,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 12.dp, vertical = 4.dp),
		horizontalArrangement = Arrangement.spacedBy(10.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Surface(
			shape = CircleShape,
			color = MaterialTheme.colorScheme.secondaryContainer,
			contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
			tonalElevation = 2.dp,
			shadowElevation = 0.dp,
			modifier = Modifier.size(32.dp)
		) {
			Box(contentAlignment = Alignment.Center) {
				Text(
					text = currentSectionLabel.ifBlank { "#" },
					style = MaterialTheme.typography.labelLarge,
					fontWeight = FontWeight.SemiBold,
				)
			}
		}

		Box(
			modifier = Modifier
				.weight(1f)
				.clip(RoundedCornerShape(14.dp))
				.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
		) {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 8.dp, vertical = 6.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Box(
					modifier = Modifier
						.clip(RoundedCornerShape(10.dp))
						.background(MaterialTheme.colorScheme.primaryContainer)
						.padding(horizontal = 10.dp, vertical = 4.dp)
				) {
					Text(
						text = pathLabel,
						style = MaterialTheme.typography.labelMedium,
						color = MaterialTheme.colorScheme.onPrimaryContainer,
					)
				}
			}
		}
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaultV2ItemCard(
	item: VaultV2Item,
	boundPassword: PasswordEntry?,
	appSettings: AppSettings,
	selected: Boolean,
	onClick: () -> Unit,
	onLongClick: () -> Unit,
) {
	val icon = when (item.type) {
		VaultV2ItemType.PASSWORD -> Icons.Default.Lock
		VaultV2ItemType.AUTHENTICATOR -> Icons.Default.Security
		VaultV2ItemType.NOTE -> Icons.Default.Description
		VaultV2ItemType.PASSKEY -> Icons.Default.VpnKey
		VaultV2ItemType.BANK_CARD -> Icons.Default.CreditCard
		VaultV2ItemType.DOCUMENT -> Icons.Default.Badge
	}
	val unmatchedIconStrategy = UnmatchedIconHandlingStrategy.DEFAULT_ICON

	val passwordIconSource = item.passwordEntry ?: boundPassword
	val totpData = remember(item.totpItem?.itemData) {
		item.totpItem?.itemData?.let { raw ->
			runCatching { Json.decodeFromString<TotpData>(raw) }.getOrNull()
		}
	}
	val iconWebsite = when {
		passwordIconSource != null -> passwordIconSource.website
		item.type == VaultV2ItemType.PASSKEY -> {
			val iconUrl = item.passkeyEntry?.iconUrl?.trim().orEmpty()
			if (iconUrl.isNotBlank()) {
				iconUrl
			} else {
				normalizeVaultV2PasskeyWebsite(item.passkeyEntry?.rpId)
			}
		}
		else -> totpData?.link?.trim().orEmpty()
	}
	val iconTitle = when {
		passwordIconSource != null -> passwordIconSource.title
		item.type == VaultV2ItemType.PASSKEY -> {
			item.passkeyEntry?.rpName?.ifBlank { item.title } ?: item.title
		}
		else -> totpData?.issuer?.ifBlank { item.title } ?: item.title
	}
	val iconAppPackage = when {
		passwordIconSource != null -> passwordIconSource.appPackageName
		else -> totpData?.associatedApp?.trim().orEmpty()
	}
	val customIconType = when {
		passwordIconSource != null -> passwordIconSource.customIconType
		else -> totpData?.customIconType ?: PASSWORD_ICON_TYPE_NONE
	}
	val customIconValue = when {
		passwordIconSource != null -> passwordIconSource.customIconValue
		else -> totpData?.customIconValue
	}
	val simpleIcon = if (customIconType == PASSWORD_ICON_TYPE_SIMPLE) {
		rememberSimpleIconBitmap(
			slug = customIconValue,
			tintColor = MaterialTheme.colorScheme.primary,
			enabled = true
		)
	} else {
		null
	}
	val uploadedIcon = if (customIconType == PASSWORD_ICON_TYPE_UPLOADED) {
		rememberUploadedPasswordIcon(customIconValue)
	} else {
		null
	}
	val autoMatchedSimpleIcon = rememberAutoMatchedSimpleIcon(
		website = iconWebsite,
		title = iconTitle,
		appPackageName = iconAppPackage.ifBlank { null },
		tintColor = MaterialTheme.colorScheme.primary,
		enabled = customIconType == PASSWORD_ICON_TYPE_NONE
	)
	val favicon = if (iconWebsite.isNotBlank()) {
		takagi.ru.monica.autofill_ng.ui.rememberFavicon(
			url = iconWebsite,
			enabled = autoMatchedSimpleIcon.resolved && autoMatchedSimpleIcon.slug == null
		)
	} else {
		null
	}
	val appIcon = if (iconAppPackage.isNotBlank()) {
		takagi.ru.monica.autofill_ng.ui.rememberAppIcon(iconAppPackage)
	} else {
		null
	}
	val authenticatorState = when (item.type) {
		VaultV2ItemType.PASSWORD -> {
			val authenticatorKey = item.passwordEntry?.authenticatorKey.orEmpty()
			if (authenticatorKey.isBlank()) {
				null
			} else {
				rememberPasswordAuthenticatorDisplayState(
					authenticatorKey = authenticatorKey,
					timeOffsetSeconds = appSettings.totpTimeOffset,
					smoothProgress = appSettings.validatorSmoothProgress
				)
			}
		}
		VaultV2ItemType.AUTHENTICATOR -> {
			rememberVaultV2TotpDisplayState(
				totpData = totpData,
				timeOffsetSeconds = appSettings.totpTimeOffset,
				smoothProgress = appSettings.validatorSmoothProgress
			)
		}
		else -> null
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
			when {
				simpleIcon != null -> {
					Image(
						bitmap = simpleIcon,
						contentDescription = null,
						contentScale = ContentScale.Fit,
						modifier = Modifier.size(40.dp).padding(2.dp)
					)
				}
				uploadedIcon != null -> {
					Image(
						bitmap = uploadedIcon,
						contentDescription = null,
						contentScale = ContentScale.Fit,
						modifier = Modifier.size(40.dp).padding(2.dp)
					)
				}
				autoMatchedSimpleIcon.bitmap != null -> {
					Image(
						bitmap = autoMatchedSimpleIcon.bitmap,
						contentDescription = null,
						contentScale = ContentScale.Fit,
						modifier = Modifier.size(40.dp).padding(2.dp)
					)
				}
				favicon != null -> {
					Image(
						bitmap = favicon,
						contentDescription = null,
						contentScale = ContentScale.Crop,
						modifier = Modifier.size(40.dp).clip(CircleShape)
					)
				}
				appIcon != null -> {
					Image(
						bitmap = appIcon,
						contentDescription = null,
						contentScale = ContentScale.Crop,
						modifier = Modifier.size(40.dp).clip(CircleShape)
					)
				}
				shouldShowFallbackSlot(unmatchedIconStrategy) -> {
					UnmatchedIconFallback(
						strategy = unmatchedIconStrategy,
						primaryText = iconWebsite,
						secondaryText = iconTitle,
						defaultIcon = icon,
						iconSize = 40.dp
					)
				}
			}
			Spacer(modifier = Modifier.width(2.dp))

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
				authenticatorState?.let { state ->
					VaultV2AuthenticatorInlineRow(
						state = state,
						smoothProgress = appSettings.validatorSmoothProgress,
					)
				}
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

@Composable
private fun VaultV2AuthenticatorInlineRow(
	state: PasswordAuthenticatorDisplayState,
	smoothProgress: Boolean,
) {
	val progressColor = when {
		(state.remainingSeconds ?: Int.MAX_VALUE) <= 5 -> MaterialTheme.colorScheme.error
		else -> MaterialTheme.colorScheme.primary
	}

	Column(
		modifier = Modifier.fillMaxWidth(),
		verticalArrangement = Arrangement.spacedBy(4.dp),
	) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Icon(
				imageVector = Icons.Default.Security,
				contentDescription = null,
				modifier = Modifier.size(14.dp),
				tint = progressColor,
			)
			Text(
				text = state.code,
				style = MaterialTheme.typography.titleSmall.copy(
					fontWeight = FontWeight.SemiBold,
					fontFamily = FontFamily.Monospace,
				),
				color = progressColor,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.weight(1f),
			)
			state.remainingSeconds?.let { remaining ->
				Text(
					text = stringResource(R.string.password_card_authenticator_seconds, remaining),
					style = MaterialTheme.typography.labelSmall.copy(
						fontFamily = FontFamily.Monospace,
					),
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
		}
		state.progress?.let { progress ->
			val animatedProgress = if (smoothProgress) {
				animateFloatAsState(
					targetValue = progress.coerceIn(0f, 1f),
					animationSpec = tween(durationMillis = 80, easing = LinearEasing),
					label = "vault_v2_auth_progress",
				).value
			} else {
				progress.coerceIn(0f, 1f)
			}
			LinearProgressIndicator(
				progress = { animatedProgress },
				modifier = Modifier
					.fillMaxWidth()
					.height(4.dp),
				color = progressColor,
				trackColor = MaterialTheme.colorScheme.surfaceVariant,
			)
		}
	}
}

@Composable
private fun rememberVaultV2TotpDisplayState(
	totpData: TotpData?,
	timeOffsetSeconds: Int,
	smoothProgress: Boolean,
): PasswordAuthenticatorDisplayState? {
	val normalizedTotpData = remember(totpData) {
		totpData?.let(::normalizeVaultV2TotpData)
	} ?: return null

	val currentTimeMillis by produceState(
		initialValue = System.currentTimeMillis(),
		key1 = normalizedTotpData,
		key2 = timeOffsetSeconds,
		key3 = smoothProgress,
	) {
		while (true) {
			val now = System.currentTimeMillis()
			value = now
			val waitMillis = if (smoothProgress) {
				50L
			} else {
				(1000L - (now % 1000L)).coerceAtLeast(16L)
			}
			delay(waitMillis)
		}
	}
	val currentSeconds = currentTimeMillis / 1000L
	val rawCode = remember(normalizedTotpData, currentSeconds, timeOffsetSeconds) {
		when (normalizedTotpData.otpType) {
			OtpType.HOTP -> TotpGenerator.generateOtp(normalizedTotpData)
			else -> TotpGenerator.generateOtp(
				totpData = normalizedTotpData,
				timeOffset = timeOffsetSeconds,
				currentSeconds = currentSeconds,
			)
		}
	}
	val formattedCode = remember(rawCode, normalizedTotpData.otpType) {
		formatVaultV2OtpCode(rawCode, normalizedTotpData.otpType)
	}

	return if (normalizedTotpData.otpType == OtpType.HOTP) {
		PasswordAuthenticatorDisplayState(
			code = formattedCode,
			remainingSeconds = null,
			progress = null,
		)
	} else {
		val remainingSeconds = remember(normalizedTotpData, currentSeconds, timeOffsetSeconds) {
			TotpGenerator.getRemainingSeconds(
				period = normalizedTotpData.period,
				timeOffset = timeOffsetSeconds,
				currentSeconds = currentSeconds,
			)
		}
		val progress = remember(
			normalizedTotpData,
			currentTimeMillis,
			currentSeconds,
			timeOffsetSeconds,
			smoothProgress,
		) {
			if (smoothProgress) {
				val periodMillis = (normalizedTotpData.period * 1000L).coerceAtLeast(1000L)
				val correctedMillis = currentTimeMillis + (timeOffsetSeconds * 1000L)
				val elapsedInPeriod = ((correctedMillis % periodMillis) + periodMillis) % periodMillis
				(elapsedInPeriod.toFloat() / periodMillis.toFloat()).coerceIn(0f, 1f)
			} else {
				TotpGenerator.getProgress(
					period = normalizedTotpData.period,
					timeOffset = timeOffsetSeconds,
					currentSeconds = currentSeconds,
				).coerceIn(0f, 1f)
			}
		}
		PasswordAuthenticatorDisplayState(
			code = formattedCode,
			remainingSeconds = remainingSeconds,
			progress = progress,
		)
	}
}

private fun normalizeVaultV2TotpData(data: TotpData): TotpData {
	val safePeriod = data.period.takeIf { it > 0 } ?: 30
	val safeDigits = data.digits.coerceIn(4, 10)
	return if (safePeriod == data.period && safeDigits == data.digits) {
		data
	} else {
		data.copy(period = safePeriod, digits = safeDigits)
	}
}

private fun formatVaultV2OtpCode(code: String, otpType: OtpType): String {
	return when (otpType) {
		OtpType.STEAM -> {
			if (code.length == 5) {
				"${code.substring(0, 2)} ${code.substring(2)}"
			} else {
				code
			}
		}
		else -> {
			when (code.length) {
				6 -> "${code.substring(0, 3)} ${code.substring(3)}"
				8 -> "${code.substring(0, 4)} ${code.substring(4)}"
				else -> code
			}
		}
	}
}

private fun vaultV2PlainSingleLine(raw: String): String {
	return raw.lineSequence()
		.map { it.trim() }
		.firstOrNull { it.isNotEmpty() }
		.orEmpty()
}

private fun vaultV2BankCardSubtitle(
	data: BankCardData?,
	fallbackNotes: String,
): String {
	if (data == null) {
		return fallbackNotes.ifBlank { "-" }
	}
	return listOf(
		data.bankName.takeIf { it.isNotBlank() },
		data.cardholderName.takeIf { it.isNotBlank() },
		vaultV2MaskedCardNumber(data.cardNumber),
	).filterNotNull()
		.joinToString(" · ")
		.ifBlank { fallbackNotes.ifBlank { "-" } }
}

private fun vaultV2DocumentSubtitle(
	data: DocumentData?,
	fallbackNotes: String,
): String {
	if (data == null) {
		return fallbackNotes.ifBlank { "-" }
	}
	return listOf(
		data.fullName.takeIf { it.isNotBlank() },
		data.documentNumber.takeIf { it.isNotBlank() }?.let(::vaultV2MaskedDocumentNumber),
		data.issuedBy.takeIf { it.isNotBlank() },
	).filterNotNull()
		.joinToString(" · ")
		.ifBlank { fallbackNotes.ifBlank { "-" } }
}

private fun vaultV2MaskedCardNumber(cardNumber: String): String? {
	val compact = cardNumber.filter { it.isDigit() }
	if (compact.isBlank()) return null
	val tail = compact.takeLast(4)
	return "•••• $tail"
}

private fun vaultV2MaskedDocumentNumber(documentNumber: String): String {
	val trimmed = documentNumber.trim()
	if (trimmed.length <= 4) return trimmed
	return buildString(trimmed.length) {
		repeat(trimmed.length - 4) { append('•') }
		append(trimmed.takeLast(4))
	}
}

private fun dedupeExactVaultItems(items: List<VaultV2Item>): List<VaultV2Item> {
	if (items.size <= 1) return items

	val indexByKey = items.mapIndexed { index, item -> item.key to index }.toMap()
	return items
		.groupBy(::buildVaultV2ExactDisplayKey)
		.values
		.mapNotNull(::pickBestVaultV2Item)
		.sortedBy { indexByKey[it.key] ?: Int.MAX_VALUE }
}

private fun buildVaultV2ExactDisplayKey(item: VaultV2Item): String {
	return when (item.type) {
		VaultV2ItemType.PASSWORD -> {
			val entry = item.passwordEntry
			listOf(
				item.type.name,
				normalizeVaultV2ComparableText(item.title),
				normalizeVaultV2ComparableText(entry?.username.orEmpty()),
				normalizeVaultV2Website(entry?.website.orEmpty()),
				entry?.password.orEmpty(),
			).joinToString("|")
		}

		VaultV2ItemType.AUTHENTICATOR -> {
			val secureItem = item.totpItem
			listOf(
				item.type.name,
				buildVaultV2SourceKey(
					categoryId = secureItem?.categoryId,
					keepassDatabaseId = secureItem?.keepassDatabaseId,
					keepassEntryUuid = secureItem?.keepassEntryUuid,
					keepassGroupPath = secureItem?.keepassGroupPath,
					bitwardenVaultId = secureItem?.bitwardenVaultId,
					bitwardenCipherId = secureItem?.bitwardenCipherId,
					bitwardenFolderId = secureItem?.bitwardenFolderId,
				),
				normalizeVaultV2ComparableText(item.title),
				normalizeVaultV2ComparableText(item.subtitle),
				secureItem?.itemData.orEmpty(),
				secureItem?.notes.orEmpty(),
			).joinToString("|")
		}

		VaultV2ItemType.NOTE,
		VaultV2ItemType.BANK_CARD,
		VaultV2ItemType.DOCUMENT -> {
			val secureItem = item.secureItem
			listOf(
				item.type.name,
				buildVaultV2SourceKey(
					categoryId = secureItem?.categoryId,
					keepassDatabaseId = secureItem?.keepassDatabaseId,
					keepassEntryUuid = secureItem?.keepassEntryUuid,
					keepassGroupPath = secureItem?.keepassGroupPath,
					bitwardenVaultId = secureItem?.bitwardenVaultId,
					bitwardenCipherId = secureItem?.bitwardenCipherId,
					bitwardenFolderId = secureItem?.bitwardenFolderId,
				),
				normalizeVaultV2ComparableText(item.title),
				normalizeVaultV2ComparableText(item.subtitle),
				secureItem?.itemData.orEmpty(),
				secureItem?.notes.orEmpty(),
			).joinToString("|")
		}

		VaultV2ItemType.PASSKEY -> {
			val passkey = item.passkeyEntry
			listOf(
				item.type.name,
				normalizeVaultV2ComparableText(item.title),
				normalizeVaultV2ComparableText(item.subtitle),
				normalizeVaultV2ComparableText(passkey?.rpId.orEmpty()),
				normalizeVaultV2ComparableText(passkey?.userName.orEmpty()),
				normalizeVaultV2ComparableText(passkey?.userDisplayName.orEmpty()),
				normalizeVaultV2ComparableText(passkey?.notes.orEmpty()),
			).joinToString("|")
		}
	}
}

private fun pickBestVaultV2Item(candidates: List<VaultV2Item>): VaultV2Item? {
	return candidates.maxWithOrNull(
		compareBy<VaultV2Item> { it.subtitle.length }
			.thenBy { it.searchText.length }
			.thenBy { if (it.isFavorite) 1 else 0 }
			.thenBy { vaultV2UpdatedAtMillis(it) }
	)
}

private fun buildVaultV2SourceKey(
	categoryId: Long?,
	keepassDatabaseId: Long?,
	keepassEntryUuid: String?,
	keepassGroupPath: String?,
	bitwardenVaultId: Long?,
	bitwardenCipherId: String?,
	bitwardenFolderId: String?,
): String {
	return when {
		keepassDatabaseId != null -> {
			"kp:$keepassDatabaseId:${keepassEntryUuid.orEmpty()}:${keepassGroupPath.orEmpty()}"
		}
		bitwardenVaultId != null -> {
			"bw:$bitwardenVaultId:${bitwardenCipherId.orEmpty()}:${bitwardenFolderId.orEmpty()}"
		}
		else -> "local:${categoryId ?: -1L}"
	}
}

private fun normalizeVaultV2ComparableText(value: String): String {
	return value.trim().lowercase(Locale.ROOT)
}

private fun normalizeVaultV2Website(value: String): String {
	val raw = value.trim()
	if (raw.isEmpty()) return ""
	return raw
		.lowercase(Locale.ROOT)
		.removePrefix("http://")
		.removePrefix("https://")
		.removePrefix("www.")
		.trimEnd('/')
}

private fun vaultV2UpdatedAtMillis(item: VaultV2Item): Long {
	return when (item.type) {
		VaultV2ItemType.PASSWORD -> item.passwordEntry?.updatedAt?.time ?: Long.MIN_VALUE
		VaultV2ItemType.AUTHENTICATOR -> item.totpItem?.updatedAt?.time ?: Long.MIN_VALUE
		VaultV2ItemType.NOTE,
		VaultV2ItemType.BANK_CARD,
		VaultV2ItemType.DOCUMENT -> item.secureItem?.updatedAt?.time ?: Long.MIN_VALUE
		VaultV2ItemType.PASSKEY -> item.passkeyEntry?.lastUsedAt ?: Long.MIN_VALUE
	}
}

private fun normalizeVaultV2PasskeyWebsite(rpId: String?): String {
	val trimmed = rpId?.trim().orEmpty()
	if (trimmed.isBlank()) return ""
	return if (trimmed.contains("://")) {
		trimmed
	} else {
		"https://$trimmed"
	}
}

private fun firstLetterGroup(raw: String): String {
	val first = raw.trim().firstOrNull()?.uppercaseChar() ?: return "#"
	return if (first in 'A'..'Z') first.toString() else "#"
}

private fun normalizedVaultV2SortKey(raw: String): String {
	val trimmed = raw.trim()
	if (trimmed.isEmpty()) return "#"
	val latin = vaultV2Transliterator.transliterate(trimmed)
	val normalized = buildString(latin.length) {
		latin.forEach { char ->
			when {
				char.isLetterOrDigit() -> append(char)
				char.isWhitespace() && isNotEmpty() && last() != ' ' -> append(' ')
			}
		}
	}.trim()
	return normalized.ifEmpty { trimmed }
}

private fun vaultV2LazyIndexForItemIndex(
	sectionLayouts: List<VaultV2SectionLayout>,
	targetItemIndex: Int,
): Int {
	for (section in sectionLayouts) {
		val sectionEnd = section.itemStartIndex + section.items.size
		if (targetItemIndex in section.itemStartIndex until sectionEnd) {
			return section.firstItemLazyIndex + (targetItemIndex - section.itemStartIndex)
		}
	}
	return sectionLayouts.lastOrNull()?.let { lastSection ->
		lastSection.firstItemLazyIndex + lastSection.items.lastIndex.coerceAtLeast(0)
	} ?: 0
}

private fun vaultV2ItemIndexForLazyIndex(
	sectionLayouts: List<VaultV2SectionLayout>,
	lazyIndex: Int,
): Int {
	for (section in sectionLayouts) {
		val headerIndex = section.firstItemLazyIndex - 1
		val lastItemLazyIndex = section.firstItemLazyIndex + section.items.lastIndex.coerceAtLeast(0)
		if (lazyIndex <= headerIndex) {
			return section.itemStartIndex
		}
		if (lazyIndex in section.firstItemLazyIndex..lastItemLazyIndex) {
			return section.itemStartIndex + (lazyIndex - section.firstItemLazyIndex)
		}
	}
	return sectionLayouts.lastOrNull()?.let { lastSection ->
		lastSection.itemStartIndex + lastSection.items.lastIndex.coerceAtLeast(0)
	} ?: 0
}

private fun vaultV2SectionTitleForLazyIndex(
	sectionLayouts: List<VaultV2SectionLayout>,
	lazyIndex: Int,
): String? {
	for (section in sectionLayouts) {
		val headerIndex = section.firstItemLazyIndex - 1
		val lastItemLazyIndex = section.firstItemLazyIndex + section.items.lastIndex.coerceAtLeast(0)
		if (lazyIndex <= headerIndex) {
			return section.title
		}
		if (lazyIndex in section.firstItemLazyIndex..lastItemLazyIndex) {
			return section.title
		}
	}
	return sectionLayouts.lastOrNull()?.title
}

