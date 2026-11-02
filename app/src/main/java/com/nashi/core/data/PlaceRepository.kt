package com.nashi.core.data

import com.nashi.core.model.HomeRoom
import com.nashi.core.model.StorageSpace
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 房间/空间管理: 首次创建用随机 id, 编辑保留原 id/version 并推进 updatedAt;
 * 删除走软删除墓碑, 让下一次同步能把它当作一条变更推上去。
 */
@Singleton
class PlaceRepository @Inject constructor(
    private val dao: NashiDao,
) {
    val rooms: Flow<List<HomeRoom>> = dao.roomsFlow()

    fun spacesOf(roomId: String): Flow<List<StorageSpace>> = dao.spacesFlow(roomId)

    suspend fun saveRoom(id: String?, name: String, width: Float, height: Float, depth: Float): String {
        val old = id?.let { dao.roomById(it) }
        val now = System.currentTimeMillis()
        val room = HomeRoom(
            id = old?.id ?: UUID.randomUUID().toString(),
            name = name,
            width = width.coerceAtLeast(0f),
            height = height.coerceAtLeast(0f),
            depth = depth.coerceAtLeast(0f),
            updatedAt = now,
            updatedBy = old?.updatedBy ?: "",
            version = (old?.version ?: 0) + 1,
        )
        dao.upsertRooms(listOf(room))
        return room.id
    }

    /** 删除房间并连带软删除其空间; 物品保留, 避免误删库存记录。 */
    suspend fun deleteRoom(id: String) {
        val now = System.currentTimeMillis()
        dao.deleteRoom(id, now)
        dao.deleteSpacesOfRoom(id, now)
    }

    suspend fun saveSpace(
        id: String?,
        roomId: String,
        name: String,
        type: String,
        width: Float,
        depth: Float,
        shelfHeight: Float,
        columns: Int,
    ): String {
        val old = id?.let { dao.spaceById(it) }
        val now = System.currentTimeMillis()
        val space = StorageSpace(
            id = old?.id ?: UUID.randomUUID().toString(),
            roomId = roomId,
            name = name,
            type = type,
            width = width.coerceAtLeast(0f),
            depth = depth.coerceAtLeast(0f),
            shelfHeight = shelfHeight.coerceAtLeast(0f),
            columns = columns.coerceAtLeast(1),
            positionIndex = old?.positionIndex ?: 0,
            updatedAt = now,
            updatedBy = old?.updatedBy ?: "",
            version = (old?.version ?: 0) + 1,
        )
        dao.upsertSpaces(listOf(space))
        return space.id
    }

    suspend fun deleteSpace(id: String) {
        dao.deleteSpace(id, System.currentTimeMillis())
    }
}
