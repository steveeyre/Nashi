package com.nashi.core.data

import android.content.Context
import com.nashi.core.media.PhotoStore
import com.nashi.core.model.Item
import com.nashi.core.model.ItemFieldValue
import com.nashi.core.model.StockTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ItemRepository @Inject constructor(
    private val dao: NashiDao,
    @ApplicationContext private val ctx: Context,
) {
    private val prefs = ctx.getSharedPreferences("nashi", Context.MODE_PRIVATE)

    fun searchItems(q: String) = dao.searchItems(q.ifBlank { "" })

    suspend fun ensureSeed() {
        if (dao.allCategories().isEmpty()) {
            dao.upsertCategories(SeedData.categories)
            dao.upsertCategoryFields(SeedData.fields)
            dao.upsertRooms(SeedData.rooms)
            dao.upsertSpaces(SeedData.spaces)
            prefs.edit().putBoolean(KEY_ROOMS_V2, true).apply()
            return
        }
        seedRoomsOnce()
    }

    /**
     * 老安装只 seed 过 4 个房间, 这里一次性补齐新的 9 个默认房间。
     * 用偏好设置做一次性标记: 用户之后删掉的房间不会被反复加回来。
     */
    private suspend fun seedRoomsOnce() {
        if (prefs.getBoolean(KEY_ROOMS_V2, false)) return
        val upserts = SeedData.roomPresetUpserts(dao.allRooms(), System.currentTimeMillis())
        if (upserts.isNotEmpty()) dao.upsertRooms(upserts)
        prefs.edit().putBoolean(KEY_ROOMS_V2, true).apply()
    }

    fun photoFile(): File {
        val dir = File(ctx.filesDir, "photos").apply { mkdirs() }
        return File(dir, UUID.randomUUID().toString() + ".jpg")
    }

    // 保存物品: 新建 inTime=now, 编辑保留原 inTime; 数量差记 StockTransaction
    suspend fun saveItem(
        existingId: String?,
        name: String, categoryId: String, brand: String,
        quantity: Double, unit: String, barcode: String,
        photoPaths: List<String>, roomId: String, spaceId: String,
        expiryDate: Long?, minStock: Double,
        dynValues: Map<String, String>,
        width: Float = 0f, height: Float = 0f, depth: Float = 0f,
        deviceId: String = "local",
    ): String {
        val now = System.currentTimeMillis()
        val old = existingId?.let { dao.itemById(it) }
        val id = old?.id ?: UUID.randomUUID().toString()
        dao.upsertItem(
            Item(
                id = id, name = name, categoryId = categoryId, brand = brand,
                quantity = quantity, unit = unit, barcode = barcode,
                photoPathsCsv = photoPaths.joinToString("|"),
                roomId = roomId, spaceId = spaceId,
                width = width, height = height, depth = depth,
                inTime = old?.inTime ?: now,
                expiryDate = expiryDate, minStock = minStock,
                updatedAt = now, updatedBy = deviceId, version = (old?.version ?: 0) + 1,
            )
        )
        dao.upsertFieldValues(dynValues.map { (k, v) -> ItemFieldValue(id, k, v) })
        val delta = quantity - (old?.quantity ?: 0.0)
        if (delta != 0.0) {
            dao.insertTransactions(
                listOf(StockTransaction(UUID.randomUUID().toString(), id, delta, if (old == null) "in" else "check", now, deviceId))
            )
        }
        return id
    }

    /** 快速出入库: 改数量并记一条流水; 数量不低于 0。 */
    suspend fun adjustQuantity(item: Item, delta: Double, kind: String, deviceId: String = "local") {
        val now = System.currentTimeMillis()
        val next = (item.quantity + delta).coerceAtLeast(0.0)
        val applied = next - item.quantity
        if (applied == 0.0) return
        dao.upsertItem(
            item.copy(
                quantity = next, updatedAt = now, updatedBy = deviceId, version = item.version + 1,
            )
        )
        dao.insertTransactions(
            listOf(StockTransaction(UUID.randomUUID().toString(), item.id, applied, kind, now, deviceId))
        )
    }

    /** 软删除(墓碑随同步下发), 可由 restoreItem 撤销。 */
    suspend fun deleteItem(id: String, deviceId: String = "local") {
        val old = dao.itemById(id) ?: return
        dao.upsertItem(
            old.copy(
                isDeleted = true, updatedAt = System.currentTimeMillis(),
                updatedBy = deviceId, version = old.version + 1,
            )
        )
    }

    /** 回收站彻底删除: 连同字段值/标签关联一起清除, 库存流水保留。 */
    suspend fun purgeItem(id: String) {
        val item = dao.itemById(id)
        dao.deleteItemTagsOf(id)
        dao.deleteFieldValuesOf(id)
        dao.hardDeleteItem(id)
        item?.photoPathsCsv?.split("|")?.forEach { PhotoStore.delete(it) }
        ctx.addPurgedItem(id)
    }

    /** 每周维护: 清除过期墓碑(连同照片/字段/标签) 与孤儿关联行, 返回清除的墓碑数。 */
    suspend fun runMaintenance(
        now: Long = System.currentTimeMillis(),
        retentionDays: Long = TombstonePolicy.RETENTION_DAYS,
    ): Int {
        val expired = dao.allItems().filter { TombstonePolicy.isExpired(it, now, retentionDays) }
        expired.forEach { purgeItem(it.id) }
        dao.deleteOrphanItemTags()
        dao.deleteOrphanFieldValues()
        return expired.size
    }

    /** 批量软删除(墓碑随同步下发)。 */
    suspend fun deleteItems(ids: Collection<String>) {
        ids.forEach { deleteItem(it) }
    }

    /** 批量移动房间/空间, 数量与流水不变。 */
    suspend fun moveItems(
        ids: Collection<String>, roomId: String, spaceId: String, deviceId: String = "local",
    ) {
        val now = System.currentTimeMillis()
        val rows = ids.mapNotNull { dao.itemById(it) }
            .filter { !it.isDeleted }
            .map {
                it.copy(
                    roomId = roomId, spaceId = spaceId, updatedAt = now,
                    updatedBy = deviceId, version = it.version + 1,
                )
            }
        if (rows.isNotEmpty()) dao.upsertItems(rows)
    }

    /** 批量改分类, 数量与流水不变。 */
    suspend fun setCategory(ids: Collection<String>, categoryId: String, deviceId: String = "local") {
        val now = System.currentTimeMillis()
        val rows = ids.mapNotNull { dao.itemById(it) }
            .filter { !it.isDeleted }
            .map {
                it.copy(
                    categoryId = categoryId, updatedAt = now,
                    updatedBy = deviceId, version = it.version + 1,
                )
            }
        if (rows.isNotEmpty()) dao.upsertItems(rows)
    }

    suspend fun restoreItem(item: Item, deviceId: String = "local") {
        dao.upsertItem(
            item.copy(
                isDeleted = false, updatedAt = System.currentTimeMillis(),
                updatedBy = deviceId, version = item.version + 1,
            )
        )
    }

    private companion object {
        const val KEY_ROOMS_V2 = "rooms_seeded_v2"
    }
}
