package com.nashi.feature.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nashi.core.data.ItemRepository
import com.nashi.core.data.NashiDao
import com.nashi.core.model.Item
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class TrashViewModel @Inject constructor(
    dao: NashiDao,
    private val repo: ItemRepository,
) : ViewModel() {
    val items: StateFlow<List<Item>> =
        dao.deletedItemsFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun restore(item: Item) {
        viewModelScope.launch { repo.restoreItem(item) }
    }

    fun purge(item: Item) {
        viewModelScope.launch { repo.purgeItem(item.id) }
    }
}
