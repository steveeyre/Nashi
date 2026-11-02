package com.nashi.feature.place

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nashi.core.data.PlaceRepository
import com.nashi.core.model.HomeRoom
import com.nashi.core.model.StorageSpace
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PlaceViewModel @Inject constructor(
    private val repo: PlaceRepository,
) : ViewModel() {

    val rooms: StateFlow<List<HomeRoom>> =
        repo.rooms.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    fun spacesOf(roomId: String): Flow<List<StorageSpace>> =
        if (roomId.isBlank()) flowOf(emptyList()) else repo.spacesOf(roomId)

    fun clearStatus() {
        _status.value = ""
    }

    fun saveRoom(id: String?, name: String, width: Float, height: Float, depth: Float, onDone: (String) -> Unit = {}) {
        val n = name.trim()
        if (n.isEmpty()) {
            _status.value = "房间名不能为空"
            return
        }
        viewModelScope.launch {
            runCatching { repo.saveRoom(id, n, width, height, depth) }
                .onSuccess { saved ->
                    _status.value = if (id == null) "已新增房间「$n」" else "已保存房间「$n」"
                    onDone(saved)
                }
                .onFailure { _status.value = "保存房间失败: ${it.message}" }
        }
    }

    fun deleteRoom(room: HomeRoom) {
        viewModelScope.launch {
            runCatching { repo.deleteRoom(room.id) }
                .onSuccess { _status.value = "已删除房间「${room.name}」" }
                .onFailure { _status.value = "删除房间失败: ${it.message}" }
        }
    }

    fun saveSpace(
        id: String?,
        roomId: String,
        name: String,
        type: String,
        width: Float,
        depth: Float,
        shelfHeight: Float,
        columns: Int,
    ) {
        val n = name.trim()
        if (n.isEmpty()) {
            _status.value = "空间名不能为空"
            return
        }
        viewModelScope.launch {
            runCatching { repo.saveSpace(id, roomId, n, type, width, depth, shelfHeight, columns) }
                .onSuccess { _status.value = if (id == null) "已新增空间「$n」" else "已保存空间「$n」" }
                .onFailure { _status.value = "保存空间失败: ${it.message}" }
        }
    }

    fun deleteSpace(space: StorageSpace) {
        viewModelScope.launch {
            runCatching { repo.deleteSpace(space.id) }
                .onSuccess { _status.value = "已删除空间「${space.name}」" }
                .onFailure { _status.value = "删除空间失败: ${it.message}" }
        }
    }
}
