package com.nashi.feature.item

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nashi.core.data.ItemRepository
import com.nashi.core.data.NashiDao
import com.nashi.core.model.Category
import com.nashi.core.model.CategoryFieldDef
import com.nashi.core.model.HomeRoom
import com.nashi.core.model.Item
import com.nashi.core.model.ItemFieldValue
import com.nashi.core.model.ItemTag
import com.nashi.core.model.StorageSpace
import com.nashi.core.model.Tag
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    dao: NashiDao,
    private val repo: ItemRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val itemId: String = savedStateHandle.get<String>("id").orEmpty()

    private data class Meta(
        val item: Item?,
        val categories: List<Category>,
        val rooms: List<HomeRoom>,
        val spaces: List<StorageSpace>,
        val tags: List<Tag>,
    )

    private data class Extras(
        val itemTags: List<ItemTag>,
        val values: List<ItemFieldValue>,
        val defs: List<CategoryFieldDef>,
    )

    private val meta = combine(
        dao.itemFlow(itemId),
        dao.categoriesFlow(),
        dao.roomsFlow(),
        dao.spacesAllFlow(),
        dao.tagsFlow(),
    ) { item, categories, rooms, spaces, tags -> Meta(item, categories, rooms, spaces, tags) }

    private val extras = combine(
        dao.itemTagsFlowOf(itemId),
        dao.fieldValuesFlowOf(itemId),
        dao.categoryFieldsFlow(),
    ) { links, values, defs -> Extras(links, values, defs) }

    val state: StateFlow<ItemDetailState?> = combine(meta, extras) { m, e ->
        val item = m.item
        if (item == null || item.isDeleted) {
            null
        } else {
            buildItemDetail(
                item = item,
                categories = m.categories,
                rooms = m.rooms,
                spaces = m.spaces,
                tags = m.tags,
                itemTags = e.itemTags,
                fieldDefs = e.defs,
                fieldValues = e.values,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        viewModelScope.launch { state.collect { _loading.value = false } }
    }

    fun adjust(delta: Double) {
        val current = state.value ?: return
        viewModelScope.launch {
            repo.adjustQuantity(current.item, delta, if (delta > 0) "in" else "out")
        }
    }

    fun delete(onDone: () -> Unit) {
        val current = state.value ?: return
        viewModelScope.launch {
            repo.deleteItem(current.item.id)
            onDone()
        }
    }
}
