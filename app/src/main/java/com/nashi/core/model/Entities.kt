package com.nashi.core.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// 同步基字段: id(uuid) + updatedAt/updatedBy/version/isDeleted
interface SyncRow {
    val id: String; val updatedAt: Long; val updatedBy: String
    val version: Int; val isDeleted: Boolean
}

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey override val id: String,
    val name: String, val icon: String = "",
    override val updatedAt: Long = System.currentTimeMillis(),
    override val updatedBy: String = "",
    override val version: Int = 1,
    override val isDeleted: Boolean = false,
) : SyncRow

@Entity(tableName = "categoryFields")
data class CategoryFieldDef(
    @PrimaryKey val id: String,
    val categoryId: String, val key: String, val label: String,
    val type: String, // text|number|date|enum|bool
    val required: Boolean = false, val optionsCsv: String = "",
)

@Entity(
    tableName = "items",
    indices = [Index("barcode"), Index("roomId"), Index("spaceId"), Index("updatedAt")],
)
data class Item(
    @PrimaryKey override val id: String,
    val name: String, val categoryId: String,
    val brand: String = "", val quantity: Double = 1.0, val unit: String = "件",
    val barcode: String = "", val qrPayload: String = "",
    val photoPathsCsv: String = "",
    val roomId: String = "", val spaceId: String = "",
    val x: Float = 0f, val y: Float = 0f, val z: Float = 0f, // cm, 相对所在空间
    val width: Float = 0f, val height: Float = 0f, val depth: Float = 0f,
    val inTime: Long = System.currentTimeMillis(), // 入库时间自动=当前
    val expiryDate: Long? = null, val minStock: Double = 0.0,
    override val updatedAt: Long = System.currentTimeMillis(),
    override val updatedBy: String = "",
    override val version: Int = 1,
    override val isDeleted: Boolean = false,
) : SyncRow

@Entity(tableName = "fieldValues", primaryKeys = ["itemId", "key"])
data class ItemFieldValue(val itemId: String, val key: String, val value: String)

@Entity(tableName = "tags")
data class Tag(
    @PrimaryKey override val id: String, val name: String,
    override val updatedAt: Long = System.currentTimeMillis(),
    override val updatedBy: String = "", override val version: Int = 1,
    override val isDeleted: Boolean = false,
) : SyncRow

@Entity(tableName = "itemTags", primaryKeys = ["itemId", "tagId"])
data class ItemTag(val itemId: String, val tagId: String)

@Entity(tableName = "rooms")
data class HomeRoom(
    @PrimaryKey override val id: String, val name: String,
    val width: Float = 0f, val height: Float = 0f, val depth: Float = 0f,
    override val updatedAt: Long = System.currentTimeMillis(),
    override val updatedBy: String = "", override val version: Int = 1,
    override val isDeleted: Boolean = false,
) : SyncRow

@Entity(tableName = "spaces", indices = [Index("roomId")])
data class StorageSpace(
    @PrimaryKey override val id: String, val roomId: String, val name: String,
    val type: String = "shelf", // shelf|box|drawer|bookcase
    val width: Float = 0f, val depth: Float = 0f, val shelfHeight: Float = 0f,
    val columns: Int = 1, val maxOverhang: Float = 10f,
    val allowOverhang: Boolean = true, val locked: Boolean = false,
    val positionIndex: Int = 0,
    override val updatedAt: Long = System.currentTimeMillis(),
    override val updatedBy: String = "", override val version: Int = 1,
    override val isDeleted: Boolean = false,
) : SyncRow

@Entity(tableName = "transactions")
data class StockTransaction(
    @PrimaryKey val id: String, // uuid,只追加
    val itemId: String, val delta: Double, val kind: String, // in|out|move|check
    val createdAt: Long = System.currentTimeMillis(), val deviceId: String = "",
)
