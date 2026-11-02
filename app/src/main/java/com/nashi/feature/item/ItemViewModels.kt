package com.nashi.feature.item

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nashi.core.data.ItemFilter
import com.nashi.core.data.ItemQuery
import com.nashi.core.data.ItemRepository
import com.nashi.core.data.ItemSort
import com.nashi.core.data.NashiDao
import com.nashi.core.lookup.BookLookupRepository
import com.nashi.core.lookup.BookLookupResult
import com.nashi.core.model.Category
import com.nashi.core.model.HomeRoom
import com.nashi.core.model.Item
import com.nashi.core.model.ItemTag
import com.nashi.core.model.StorageSpace
import com.nashi.core.model.Tag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PAGE_SIZE = 200

data class ItemListContent(val items: List<Item>, val total: Int)

@HiltViewModel
class ItemListViewModel @Inject constructor(
    private val repo: ItemRepository,
    private val dao: NashiDao,
) : ViewModel() {
    private val _filter = MutableStateFlow(ItemFilter())
    val filter: StateFlow<ItemFilter> = _filter.asStateFlow()

    private val _limit = MutableStateFlow(PAGE_SIZE)
    private val _content = MutableStateFlow(ItemListContent(emptyList(), 0))
    val content: StateFlow<ItemListContent> = _content.asStateFlow()

    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    var spaces by mutableStateOf<List<StorageSpace>>(emptyList())
        private set

    init {
        viewModelScope.launch { repo.ensureSeed() }
        viewModelScope.launch {
            combine(dao.itemsFlow(), dao.itemTagsFlow(), _filter, _limit) { all, itemTags, f, limit ->
                val tagItemIds = if (f.tagId.isEmpty()) {
                    emptySet()
                } else {
                    itemTags.filter { it.tagId == f.tagId }.map { it.itemId }.toSet()
                }
                val full = ItemQuery.apply(all, f, tagItemIds)
                ItemListContent(full.take(limit), full.size)
            }.collect { _content.value = it }
        }
    }

    val categories: StateFlow<List<Category>> =
        dao.categoriesFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val rooms: StateFlow<List<HomeRoom>> =
        dao.roomsFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val tags: StateFlow<List<Tag>> =
        dao.tagsFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQuery(v: String) = update { it.copy(keyword = v) }
    fun onCategory(id: String) = update { it.copy(categoryId = if (it.categoryId == id) "" else id) }
    fun onRoom(id: String) = update { it.copy(roomId = if (it.roomId == id) "" else id) }
    fun onTag(id: String) = update { it.copy(tagId = if (it.tagId == id) "" else id) }
    fun onLowStockOnly(v: Boolean) = update { it.copy(lowStockOnly = v) }
    fun onSort(sort: ItemSort) = update { it.copy(sort = sort) }
    fun resetFilters() = update { ItemFilter(sort = it.sort) }

    /** 列表只渲染前 limit 条, 过滤/排序仍基于全量数据。 */
    fun loadMore() {
        _limit.value += PAGE_SIZE
    }

    private fun update(block: (ItemFilter) -> ItemFilter) {
        _filter.value = block(_filter.value)
        _limit.value = PAGE_SIZE
    }

    // ---- 多选批量操作 ----
    fun toggleSelection(id: String) {
        _selection.value = if (id in _selection.value) _selection.value - id else _selection.value + id
    }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    fun selectAllVisible() {
        _selection.value = _content.value.items.map { it.id }.toSet()
    }

    fun bulkDelete() {
        val ids = _selection.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repo.deleteItems(ids)
            _selection.value = emptySet()
        }
    }

    fun bulkMove(roomId: String, spaceId: String) {
        val ids = _selection.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repo.moveItems(ids, roomId, spaceId)
            _selection.value = emptySet()
        }
    }

    fun bulkCategory(categoryId: String) {
        val ids = _selection.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repo.setCategory(ids, categoryId)
            _selection.value = emptySet()
        }
    }

    fun loadSpaces(roomId: String) {
        viewModelScope.launch {
            spaces = if (roomId.isBlank()) emptyList() else dao.spacesFlow(roomId).first()
        }
    }

    fun delete(item: Item) {
        viewModelScope.launch { repo.deleteItem(item.id) }
    }

    fun undoDelete(item: Item) {
        viewModelScope.launch { repo.restoreItem(item) }
    }
}

@HiltViewModel
class ItemEditViewModel @Inject constructor(
    private val dao: NashiDao,
    private val repo: ItemRepository,
    private val bookLookup: BookLookupRepository,
) : ViewModel() {
    var categories by mutableStateOf<List<Category>>(emptyList())
        private set
    var rooms by mutableStateOf<List<HomeRoom>>(emptyList())
        private set
    var tags by mutableStateOf<List<Tag>>(emptyList())
        private set
    var selectedTagIds by mutableStateOf<Set<String>>(emptySet())
        private set

    init {
        viewModelScope.launch {
            repo.ensureSeed()
            categories = dao.allCategories()
            launch { dao.tagsFlow().collect { tags = it } }
            dao.roomsFlow().collect { rooms = it }
        }
    }

    fun toggleTag(id: String) {
        selectedTagIds = if (id in selectedTagIds) selectedTagIds - id else selectedTagIds + id
    }

    fun photoFile() = repo.photoFile()

    fun spacesOf(roomId: String): Flow<List<StorageSpace>> =
        if (roomId.isBlank()) flowOf(emptyList()) else dao.spacesFlow(roomId)

    fun fieldDefsOf(categoryId: String) = dao.fieldDefs(categoryId)

    suspend fun lookupBook(raw: String): BookLookupResult = bookLookup.lookup(raw)

    suspend fun loadItem(id: String): Pair<Item?, Map<String, String>> {
        if (id.isBlank()) return null to emptyMap()
        val item = dao.itemById(id) ?: return null to emptyMap()
        selectedTagIds = dao.allItemTags().filter { it.itemId == id }.map { it.tagId }.toSet()
        return item to dao.fieldValuesOf(id).associate { it.key to it.value }
    }

    suspend fun save(
        existingId: String?, name: String, categoryId: String, brand: String,
        quantity: Double, unit: String, barcode: String,
        photos: List<String>, roomId: String, spaceId: String,
        expiryDate: Long?, minStock: Double, dyn: Map<String, String>,
        width: Float, height: Float, depth: Float,
    ): String {
        val id = repo.saveItem(
            existingId?.ifBlank { null }, name, categoryId, brand,
            quantity, unit, barcode, photos, roomId, spaceId,
            expiryDate, minStock, dyn,
            width = width, height = height, depth = depth,
        )
        dao.deleteItemTagsOf(id)
        if (selectedTagIds.isNotEmpty()) {
            dao.upsertItemTags(selectedTagIds.map { ItemTag(id, it) })
        }
        return id
    }
}
