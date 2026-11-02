package com.nashi.feature.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nashi.core.data.NashiDao
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HistoryRow(
    val id: String,
    val itemName: String,
    val delta: Double,
    val kind: String,
    val createdAt: Long,
)

@HiltViewModel
class StockHistoryViewModel @Inject constructor(
    private val dao: NashiDao,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val itemId: String = savedStateHandle.get<String>("item").orEmpty()

    private val _rows = MutableStateFlow<List<HistoryRow>>(emptyList())
    val rows: StateFlow<List<HistoryRow>> = _rows.asStateFlow()

    private val _title = MutableStateFlow("库存流水")
    val title: StateFlow<String> = _title.asStateFlow()

    init {
        viewModelScope.launch {
            val names = dao.allItems().associate { it.id to it.name }
            if (itemId.isNotEmpty()) {
                _title.value = names[itemId]?.let { "$it · 流水" } ?: "物品流水"
            }
            val transactions = if (itemId.isEmpty()) dao.allTransactions() else dao.transactionsOf(itemId)
            _rows.value = transactions
                .sortedByDescending { it.createdAt }
                .map {
                    HistoryRow(
                        id = it.id,
                        itemName = names[it.itemId] ?: "(已删除物品)",
                        delta = it.delta,
                        kind = it.kind,
                        createdAt = it.createdAt,
                    )
                }
        }
    }
}
