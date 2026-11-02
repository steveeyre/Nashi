package com.nashi.core.sync

import kotlinx.serialization.Serializable

@Serializable data class PullResp(val changes: ChangeSet, val nextSince: Long)
@Serializable data class ChangeSet(
    val items: List<ItemDto> = emptyList(),
    val rooms: List<RoomDto> = emptyList(),
    val spaces: List<SpaceDto> = emptyList(),
    val transactions: List<TxDto> = emptyList(),
    val categories: List<CategoryDto> = emptyList(),
    val tags: List<TagDto> = emptyList(),
    val categoryFields: List<FieldDefDto> = emptyList(),
)
@Serializable data class ItemDto(
    val id: String, val name: String, val categoryId: String, val quantity: Double,
    val roomId: String = "", val spaceId: String = "", val updatedAt: Long,
    val updatedBy: String = "", val version: Int = 1, val isDeleted: Boolean = false,
    /** 元数据随物品 LWW 整行同步; 照片文件不在协议内(用带照片的 ZIP 备份迁移)。 */
    val brand: String = "", val unit: String = "件", val barcode: String = "",
    val inTime: Long = 0L, val expiryDate: Long? = null, val minStock: Double = 0.0,
    val width: Float = 0f, val height: Float = 0f, val depth: Float = 0f,
    /** 自定义字段值：整体随物品 LWW，远端键集合视为该物品的完整集合。 */
    val fieldValues: Map<String, String> = emptyMap(),
    /** 标签 id 集合：同上，作为物品的完整标签集合同步。 */
    val tagIds: List<String> = emptyList(),
)
@Serializable data class RoomDto(val id: String, val name: String, val updatedAt: Long)
@Serializable data class SpaceDto(val id: String, val roomId: String, val name: String, val updatedAt: Long)
@Serializable data class TxDto(val id: String, val itemId: String, val delta: Double, val kind: String, val createdAt: Long, val deviceId: String = "")
@Serializable data class CategoryDto(
    val id: String, val name: String, val icon: String = "", val updatedAt: Long,
    val updatedBy: String = "", val version: Int = 1, val isDeleted: Boolean = false,
)
@Serializable data class TagDto(
    val id: String, val name: String, val updatedAt: Long,
    val updatedBy: String = "", val version: Int = 1, val isDeleted: Boolean = false,
)
/** 字段定义无时间戳：每次 push 全量覆盖，pull 时以服务端快照为准。 */
@Serializable data class FieldDefDto(
    val id: String, val categoryId: String, val key: String, val label: String,
    val type: String, val required: Boolean = false, val optionsCsv: String = "",
)

// ---- push/pull 信封与 family 管理（服务端 sync-server/ 与此对齐）----
@Serializable data class PushBody(val changes: ChangeSet, val deviceId: String = "")
@Serializable data class PushResp(val conflicts: List<ConflictDto> = emptyList())
@Serializable data class ConflictDto(val kind: String, val id: String, val serverUpdatedAt: Long, val reason: String = "")
@Serializable data class FamilyInitResp(val familyId: String, val adminToken: String)
@Serializable data class InviteBody(val familyId: String, val adminToken: String = "")
@Serializable data class InviteResp(val inviteCode: String)
@Serializable data class JoinBody(val inviteCode: String, val deviceId: String)
@Serializable data class JoinResp(val familyId: String, val deviceToken: String)
@Serializable data class DeviceInfoDto(val deviceId: String)
@Serializable data class InviteInfoDto(
    val code: String, val expiresAt: Long,
    val used: Boolean = false, val revoked: Boolean = false,
)
@Serializable data class MembersResp(
    val devices: List<DeviceInfoDto> = emptyList(),
    val invites: List<InviteInfoDto> = emptyList(),
)
@Serializable data class RevokeBody(
    val familyId: String, val adminToken: String = "",
    val inviteCode: String = "", val deviceId: String = "",
)
@Serializable data class OkResp(val ok: Boolean = false)
