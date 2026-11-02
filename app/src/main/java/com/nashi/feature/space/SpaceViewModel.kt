package com.nashi.feature.space

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nashi.core.data.NashiDao
import com.nashi.core.model.HomeRoom
import com.nashi.core.model.Item
import com.nashi.core.model.StorageSpace
import com.nashi.core.optimizer.Box
import com.nashi.core.optimizer.Shelf
import com.nashi.core.optimizer.SpaceOptimizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

@HiltViewModel
class SpaceViewModel @Inject constructor(
    private val dao: NashiDao,
) : ViewModel() {
    val rooms: StateFlow<List<HomeRoom>> =
        dao.roomsFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun spacesOf(roomId: String): Flow<List<StorageSpace>> =
        if (roomId.isBlank()) flowOf(emptyList()) else dao.spacesFlow(roomId)

    fun itemsOfSpace(roomId: String, spaceId: String): Flow<List<Item>> =
        if (roomId.isBlank() || spaceId.isBlank()) flowOf(emptyList())
        else dao.itemsByRoom(roomId).map { list -> list.filter { it.spaceId == spaceId } }

    suspend fun optimize(spaceId: String): SpaceOptimizer.Result {
        val empty = SpaceOptimizer.Result(emptyList(), emptyList(), 0)
        if (spaceId.isBlank()) return empty
        var space: StorageSpace? = null
        for (room in dao.allRooms()) {
            val hit = dao.spacesOf(room.id).firstOrNull { it.id == spaceId }
            if (hit != null) { space = hit; break }
        }
        val sp = space ?: return empty
        val items = dao.itemsByRoom(sp.roomId).first().filter { it.spaceId == spaceId }
        if (items.isEmpty()) return empty
        val boxes = items.map { item ->
            Box(item.id, item.width.takeIf { it > 0 } ?: 20f, item.height.takeIf { it > 0 } ?: 20f, item.depth.takeIf { it > 0 } ?: 20f)
        }
        val cols = maxOf(1, sp.columns)
        val shelfW = (sp.width.takeIf { it > 0 } ?: 100f) / cols
        val shelfH = sp.shelfHeight.takeIf { it > 0 } ?: 200f
        val shelfD = sp.depth.takeIf { it > 0 } ?: 60f
        val shelves = List(cols) { i ->
            Shelf("${sp.id}-$i", shelfW, shelfH, shelfD, sp.allowOverhang, sp.maxOverhang, sp.locked)
        }
        return withTimeout(12_000) { SpaceOptimizer.autoSort(boxes, shelves) }
    }
}
