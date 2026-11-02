package com.nashi.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nashi.core.data.NashiDao
import com.nashi.core.data.StatRow
import com.nashi.core.data.StatsAggregator
import com.nashi.core.model.Item
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 基础功能9: 分类统计(分房间/类别/品牌/标签).
 * 聚合规则在 StatsAggregator(纯 Kotlin, 可纯 JVM 测), 这里只从 DAO 取原始行;
 * 另暴露 expiringCount/lowStockCount 供提醒联动(功能8/10, 同一套规则)。
 */
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val dao: NashiDao,
) : ViewModel() {

    private val tick = MutableStateFlow(0)

    fun refresh() {
        tick.value += 1
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> statsFlow(loader: suspend () -> T, initial: T): StateFlow<T> =
        tick.flatMapLatest { flow { emit(loader()) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initial)

    val byRoom: StateFlow<List<StatRow>> = statsFlow({ StatsAggregator.statByRoom(dao.allItems()) }, emptyList())

    val bySpace: StateFlow<List<StatRow>> = statsFlow({ StatsAggregator.statBySpace(dao.allItems()) }, emptyList())

    val byCategory: StateFlow<List<StatRow>> = statsFlow({ StatsAggregator.statByCategory(dao.allItems()) }, emptyList())

    val byBrand: StateFlow<List<StatRow>> = statsFlow({ StatsAggregator.statByBrand(dao.allItems()) }, emptyList())

    val byTag: StateFlow<List<StatRow>> = statsFlow(
        { StatsAggregator.statByTag(dao.allItems(), dao.allTags(), dao.allItemTags()) },
        emptyList(),
    )

    /** roomId -> 房间名, 用于把统计 k(id) 还原为友好名. */
    val roomNames: StateFlow<Map<String, String>> =
        statsFlow({ dao.allRooms().associate { it.id to it.name } }, emptyMap())

    /** spaceId -> 空间名. */
    val spaceNames: StateFlow<Map<String, String>> =
        statsFlow({ dao.allSpaces().associate { it.id to it.name } }, emptyMap())

    /** categoryId -> 分类名. */
    val categoryNames: StateFlow<Map<String, String>> =
        statsFlow({ dao.allCategories().associate { it.id to it.name } }, emptyMap())

    /** 30 天内过期物品件数(提醒联动). */
    val expiringCount: StateFlow<Int> = statsFlow(
        { StatsAggregator.expiringBefore(dao.allItems(), System.currentTimeMillis() + THIRTY_DAYS_MS).size },
        0,
    )

    /** 低于安全库存物品件数(提醒联动). */
    val lowStockCount: StateFlow<Int> = statsFlow({ StatsAggregator.lowStock(dao.allItems()).size }, 0)

    val expiringItems: StateFlow<List<Item>> = statsFlow(
        { StatsAggregator.expiringBefore(dao.allItems(), System.currentTimeMillis() + THIRTY_DAYS_MS) },
        emptyList(),
    )

    val lowStockItems: StateFlow<List<Item>> = statsFlow(
        { StatsAggregator.lowStock(dao.allItems()) },
        emptyList(),
    )

    companion object {
        private const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000
    }
}
