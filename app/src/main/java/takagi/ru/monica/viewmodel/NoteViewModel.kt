package takagi.ru.monica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.model.NoteData
import takagi.ru.monica.repository.SecureItemRepository
import java.util.Date

/**
 * 笔记管理 ViewModel
 */
class NoteViewModel(
    private val repository: SecureItemRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val notes: StateFlow<List<SecureItem>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                repository.getItemsByType(ItemType.NOTE)
            } else {
                repository.searchItemsByType(ItemType.NOTE, query)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    suspend fun getNoteById(id: Long): SecureItem? {
        return repository.getItemById(id)
    }

    fun observeNoteById(id: Long): Flow<SecureItem?> {
        return repository.observeItemById(id)
    }

    fun addNote(
        title: String,
        content: String,
        tags: List<String> = emptyList(),
        isMarkdown: Boolean = false,
        notes: String = "",
        isFavorite: Boolean = false
    ) {
        viewModelScope.launch {
            val noteData = NoteData(
                content = content,
                tags = tags,
                isMarkdown = isMarkdown
            )

            val item = SecureItem(
                itemType = ItemType.NOTE,
                title = title,
                notes = notes,
                itemData = Json.encodeToString(noteData),
                isFavorite = isFavorite,
                createdAt = Date(),
                updatedAt = Date(),
                imagePaths = ""
            )

            repository.insertItem(item)
        }
    }

    fun updateNote(
        id: Long,
        title: String,
        content: String,
        tags: List<String> = emptyList(),
        isMarkdown: Boolean = false,
        notes: String = "",
        isFavorite: Boolean = false
    ) {
        viewModelScope.launch {
            repository.getItemById(id)?.let { existing ->
                val noteData = NoteData(
                    content = content,
                    tags = tags,
                    isMarkdown = isMarkdown
                )

                val updatedItem = existing.copy(
                    title = title,
                    notes = notes,
                    itemData = Json.encodeToString(noteData),
                    isFavorite = isFavorite,
                    updatedAt = Date()
                )

                repository.updateItem(updatedItem)
            }
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch {
            repository.getItemById(id)?.let { item ->
                repository.deleteItem(item)
            }
        }
    }

    fun toggleFavorite(id: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.updateFavoriteStatus(id, isFavorite)
        }
    }

    fun updateSortOrders(items: List<Pair<Long, Int>>) {
        viewModelScope.launch {
            repository.updateSortOrders(items)
        }
    }

    fun parseNoteData(json: String): NoteData? {
        return try {
            Json.decodeFromString<NoteData>(json)
        } catch (_: Exception) {
            null
        }
    }
}
