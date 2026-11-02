package com.nashi.feature.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nashi.core.data.NashiDao
import com.nashi.core.model.HomeRoom
import com.nashi.core.model.Item
import com.nashi.core.model.StorageSpace
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LocationViewModel @Inject constructor(
    private val dao: NashiDao,
) : ViewModel() {
    val rooms: StateFlow<List<HomeRoom>> =
        dao.roomsFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun spacesOf(roomId: String): Flow<List<StorageSpace>> =
        if (roomId.isBlank()) flowOf(emptyList()) else dao.spacesFlow(roomId)

    fun itemsOfRoom(roomId: String): Flow<List<Item>> =
        if (roomId.isBlank()) flowOf(emptyList()) else dao.itemsByRoom(roomId)

    fun itemsOfSpace(roomId: String, spaceId: String): Flow<List<Item>> =
        if (roomId.isBlank() || spaceId.isBlank()) flowOf(emptyList())
        else dao.itemsByRoom(roomId).map { list -> list.filter { it.spaceId == spaceId } }
}
