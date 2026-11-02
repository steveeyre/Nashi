package com.nashi.sync

import java.security.MessageDigest
import kotlinx.serialization.Serializable

/** 拉取一页的结果: nextSince 只由本页实际返回的行决定, 不掺服务器时钟。 */
data class PullPage(val changes: ChangeSet, val nextSince: Long)

/** push 的 LWW 合并: 较新胜出并记录冲突; 事务去重; 字段定义整表替换。 */
fun applyPush(store: FamilyStore, changes: ChangeSet): List<ConflictDto> {
    val conflicts = mutableListOf<ConflictDto>()

    for (dto in changes.items) {
        val cur = store.items[dto.id]
        if (cur == null || dto.updatedAt >= cur.updatedAt) {
            store.items[dto.id] = dto
        } else {
            conflicts += ConflictDto("item", dto.id, cur.updatedAt, "LWW: server newer")
        }
    }
    for (dto in changes.rooms) {
        val cur = store.rooms[dto.id]
        if (cur == null || dto.updatedAt >= cur.updatedAt) {
            store.rooms[dto.id] = dto
        } else {
            conflicts += ConflictDto("room", dto.id, cur.updatedAt, "LWW: server newer")
        }
    }
    for (dto in changes.spaces) {
        val cur = store.spaces[dto.id]
        if (cur == null || dto.updatedAt >= cur.updatedAt) {
            store.spaces[dto.id] = dto
        } else {
            conflicts += ConflictDto("space", dto.id, cur.updatedAt, "LWW: server newer")
        }
    }
    // 只追加: 按 id 去重, 已存在则忽略(幂等)。
    for (tx in changes.transactions) {
        store.txs.putIfAbsent(tx.id, tx)
    }
    for (dto in changes.categories) {
        val cur = store.categories[dto.id]
        if (cur == null || dto.updatedAt >= cur.updatedAt) {
            store.categories[dto.id] = dto
        } else {
            conflicts += ConflictDto("category", dto.id, cur.updatedAt, "LWW: server newer")
        }
    }
    for (dto in changes.tags) {
        val cur = store.tags[dto.id]
        if (cur == null || dto.updatedAt >= cur.updatedAt) {
            store.tags[dto.id] = dto
        } else {
            conflicts += ConflictDto("tag", dto.id, cur.updatedAt, "LWW: server newer")
        }
    }
    // 无时间戳: push 是该家庭的完整快照, 整表替换才能让删除传播。
    store.fieldDefs.clear()
    for (dto in changes.categoryFields) {
        store.fieldDefs[dto.id] = dto
    }
    return conflicts
}

fun buildPullPage(store: FamilyStore, since: Long, limit: Int): PullPage {
    val items = store.items.values.filter { it.updatedAt > since }.sortedBy { it.updatedAt }
    val rooms = store.rooms.values.filter { it.updatedAt > since }.sortedBy { it.updatedAt }
    val spaces = store.spaces.values.filter { it.updatedAt > since }.sortedBy { it.updatedAt }
    val txs = store.txs.values.filter { it.createdAt > since }.sortedBy { it.createdAt }
    val cats = store.categories.values.filter { it.updatedAt > since }.sortedBy { it.updatedAt }
    val tags = store.tags.values.filter { it.updatedAt > since }.sortedBy { it.updatedAt }
    // 字段定义无时间戳: 每次全量下发, 客户端以服务端快照覆盖本地。
    val defs = store.fieldDefs.values.sortedBy { it.id }

    val budget = limit.coerceAtLeast(1)
    val takeItems = items.take(budget)
    val takeRooms = rooms.take((budget - takeItems.size).coerceAtLeast(0))
    val takeSpaces = spaces.take((budget - takeItems.size - takeRooms.size).coerceAtLeast(0))
    val takeTxs = txs.take((budget - takeItems.size - takeRooms.size - takeSpaces.size).coerceAtLeast(0))

    val maxTs = listOf(
        takeItems.maxOfOrNull { it.updatedAt } ?: since,
        takeRooms.maxOfOrNull { it.updatedAt } ?: since,
        takeSpaces.maxOfOrNull { it.updatedAt } ?: since,
        takeTxs.maxOfOrNull { it.createdAt } ?: since,
        cats.maxOfOrNull { it.updatedAt } ?: since,
        tags.maxOfOrNull { it.updatedAt } ?: since,
    ).max()

    return PullPage(
        ChangeSet(takeItems, takeRooms, takeSpaces, takeTxs, cats, tags, defs),
        maxOf(maxTs, since),
    )
}

// ---------------------------------------------------------------------------
// 访问控制: token 只存摘要, 邀请码一次性 + 有限期 + 可撤销。
// ---------------------------------------------------------------------------

/** 设备/管理员 token 只以 SHA-256 摘要落盘, 快照泄露也拿不到可用 token。 */
fun hashToken(token: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

@Serializable data class Invite(
    val code: String,
    val familyId: String,
    val expiresAt: Long,
    val used: Boolean = false,
    val revoked: Boolean = false,
)

fun inviteUsable(invite: Invite?, now: Long): Boolean =
    invite != null && !invite.used && !invite.revoked && invite.expiresAt > now

/** 1.x 快照直接写明文 token; 加载时升级为摘要, 已升级过的原样返回。 */
fun upgradeLegacyTokens(
    legacyAdminToken: String,
    adminTokenHash: String,
    devices: Map<String, String>,
): Pair<String, Map<String, String>> {
    val admin = adminTokenHash.ifBlank {
        if (legacyAdminToken.isBlank()) "" else hashToken(legacyAdminToken)
    }
    val upgraded = devices.mapKeys { (token, _) ->
        if (token.startsWith("dev_") || token.startsWith("adm_")) hashToken(token) else token
    }
    return admin to upgraded
}

/** 管理页展示用: 设备号去重排序, 空串丢弃(同一个 deviceId 可能有多份 token)。 */
fun memberDevices(devices: Map<String, String>): List<String> =
    devices.values.filter { it.isNotBlank() }.distinct().sorted()

/** 管理页只列本家庭仍未过期的邀请码, 新的在前; 已用/已撤销也展示, 便于管理员确认。 */
fun visibleInvites(invites: Collection<Invite>, familyId: String, now: Long): List<Invite> =
    invites.filter { it.familyId == familyId && it.expiresAt > now }
        .sortedByDescending { it.expiresAt }
