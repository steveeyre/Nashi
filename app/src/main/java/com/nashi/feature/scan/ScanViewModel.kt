package com.nashi.feature.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nashi.core.data.ItemRepository
import com.nashi.core.data.NashiDao
import com.nashi.core.model.Category
import com.nashi.core.model.Item
import com.nashi.core.scan.ReceiptLine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ReceiptImport(val created: Int, val merged: Int)

// 扫码/小票入库: 条码查物、出入库数量调整、小票按行入库(可合并同名物品)
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val dao: NashiDao,
    private val repo: ItemRepository,
) : ViewModel() {

    val categories: StateFlow<List<Category>> =
        dao.categoriesFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 条码精确查: searchItems(barcode) 自带 barcode=:q 精确条件, 优先取条码完全相等者
    fun lookupByBarcode(barcode: String): Flow<Item?> =
        dao.searchItems(barcode).map { list ->
            list.firstOrNull { it.barcode == barcode } ?: list.firstOrNull()
        }

    // 出入库: delta 可正(入库)/负(出库), 经 saveItem 记录 StockTransaction, 数量下限 0
    suspend fun checkout(itemId: String, delta: Double) {
        val old = dao.itemById(itemId) ?: return
        repo.saveItem(
            existingId = itemId,
            name = old.name,
            categoryId = old.categoryId,
            brand = old.brand,
            quantity = (old.quantity + delta).coerceAtLeast(0.0),
            unit = old.unit,
            barcode = old.barcode,
            photoPaths = old.photoPathsCsv.split("|").filter { it.isNotBlank() },
            roomId = old.roomId,
            spaceId = old.spaceId,
            expiryDate = old.expiryDate,
            minStock = old.minStock,
            dynValues = dao.fieldValuesOf(itemId).associate { it.key to it.value },
            width = old.width,
            height = old.height,
            depth = old.depth,
        )
    }

    /** 小票入库: 同名物品并到已有行(加数量), 否则按所选分类新建。 */
    suspend fun importReceiptLines(
        lines: List<ReceiptLine>,
        categoryId: String = "cat-other",
    ): ReceiptImport {
        val existing = dao.allItems().filter { !it.isDeleted }.associateBy { normalize(it.name) }
        val createdIds = mutableMapOf<String, String>()
        var created = 0
        var merged = 0
        val category = categoryId.ifBlank { "cat-other" }
        for (line in lines) {
            val name = line.name.trim()
            if (name.isEmpty()) continue
            val key = normalize(name)
            val existingId = existing[key]?.id ?: createdIds[key]
            if (existingId != null) {
                checkout(existingId, line.quantity)
                merged++
            } else {
                createdIds[key] = repo.saveItem(
                    existingId = null,
                    name = name,
                    categoryId = category,
                    brand = "",
                    quantity = line.quantity,
                    unit = line.unit,
                    barcode = "",
                    photoPaths = emptyList(),
                    roomId = "",
                    spaceId = "",
                    expiryDate = null,
                    minStock = 0.0,
                    dynValues = emptyMap(),
                )
                created++
            }
        }
        return ReceiptImport(created, merged)
    }

    private fun normalize(name: String): String = name.trim().lowercase()
}
