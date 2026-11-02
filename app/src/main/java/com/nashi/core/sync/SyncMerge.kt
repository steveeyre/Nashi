package com.nashi.core.sync

import com.nashi.core.model.Item

/** 远端行 -> 本地实体; 照片/坐标等本地专属列给出默认值。 */
fun ItemDto.toItem(): Item = Item(
    id = id, name = name, categoryId = categoryId, brand = brand,
    quantity = quantity, unit = unit.ifBlank { "件" }, barcode = barcode,
    roomId = roomId, spaceId = spaceId,
    width = width, height = height, depth = depth,
    inTime = if (inTime > 0L) inTime else System.currentTimeMillis(),
    expiryDate = expiryDate, minStock = minStock,
    updatedAt = updatedAt, updatedBy = updatedBy, version = version, isDeleted = isDeleted,
)

/** LWW 胜出的远端行覆盖本地: 协议字段以远端为准, 本地专属列(照片/坐标/二维码)保留。 */
fun Item.applyRemote(dto: ItemDto): Item = copy(
    name = dto.name, categoryId = dto.categoryId, brand = dto.brand,
    quantity = dto.quantity, unit = dto.unit.ifBlank { "件" }, barcode = dto.barcode,
    roomId = dto.roomId, spaceId = dto.spaceId,
    width = dto.width, height = dto.height, depth = dto.depth,
    inTime = if (dto.inTime > 0L) dto.inTime else inTime,
    expiryDate = dto.expiryDate, minStock = dto.minStock,
    updatedAt = dto.updatedAt, updatedBy = dto.updatedBy,
    version = dto.version, isDeleted = dto.isDeleted,
)

internal fun Item.toDto(
    device: String,
    fieldValues: Map<String, String>,
    tagIds: List<String>,
) = ItemDto(
    id = id, name = name, categoryId = categoryId, brand = brand,
    quantity = quantity, unit = unit, barcode = barcode,
    roomId = roomId, spaceId = spaceId,
    inTime = inTime, expiryDate = expiryDate, minStock = minStock,
    width = width, height = height, depth = depth,
    updatedAt = updatedAt, updatedBy = updatedBy.ifBlank { device },
    version = version, isDeleted = isDeleted,
    fieldValues = fieldValues, tagIds = tagIds,
)

/** 单条远端物品的落库决策, 从 applyChanges 抽出来以便纯 JVM 覆盖。 */
sealed interface ItemMerge {
    data object Skip : ItemMerge
    data class Insert(val item: Item) : ItemMerge
    data class Overwrite(val item: Item) : ItemMerge
}

/**
 * 本机彻底删除过的 id 要忽略服务端残留墓碑, 否则回收站会反复出现同一件;
 * 其余按 LWW: 远端时间戳不早于本地才覆盖(相等视为远端胜, 保证收敛)。
 */
fun decideItemMerge(dto: ItemDto, local: Item?, purgedIds: Set<String>): ItemMerge {
    if (dto.isDeleted && dto.id in purgedIds) return ItemMerge.Skip
    if (local == null) return ItemMerge.Insert(dto.toItem())
    if (dto.updatedAt < local.updatedAt) return ItemMerge.Skip
    return ItemMerge.Overwrite(local.applyRemote(dto))
}
