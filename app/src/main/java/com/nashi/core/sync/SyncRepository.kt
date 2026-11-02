package com.nashi.core.sync

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nashi.core.data.NashiDao
import com.nashi.core.data.purgedItemIds
import com.nashi.core.model.Category
import com.nashi.core.model.CategoryFieldDef
import com.nashi.core.model.HomeRoom
import com.nashi.core.model.Item
import com.nashi.core.model.ItemFieldValue
import com.nashi.core.model.ItemTag
import com.nashi.core.model.StockTransaction
import com.nashi.core.model.StorageSpace
import com.nashi.core.model.Tag
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.syncPrefs by preferencesDataStore(name = "sync_prefs")

/** 同步绑定信息, 供设置页展示。 */
data class SyncSettings(
    val serverUrl: String = "",
    val token: String = "",
    val familyId: String = "",
    val adminToken: String = "",
    val deviceId: String = "",
) {
    val bound: Boolean get() = serverUrl.isNotBlank() && token.isNotBlank()
    val isAdmin: Boolean get() = adminToken.isNotBlank()
}

/** syncOnce() 的结果，供 SyncWorker / UI 展示使用。 */
sealed interface SyncResult {
    data class Success(val pushed: Int, val pulled: Int, val conflicts: Int) : SyncResult
    data class Skipped(val reason: String) : SyncResult
    data class Failed(val error: String) : SyncResult
}

// categoryFields 每页都随快照下发，不能计入"本页是否还有增量"，否则会一直循环。
private fun ChangeSet.isEmpty(): Boolean =
    items.isEmpty() && rooms.isEmpty() && spaces.isEmpty() && transactions.isEmpty() &&
        categories.isEmpty() && tags.isEmpty()

private fun ChangeSet.size(): Int =
    items.size + rooms.size + spaces.size + transactions.size +
        categories.size + tags.size + categoryFields.size

/**
 * Local-first 同步仓库：先 push 本地 dirty，再 pull 远端并循环至 nextSince 收敛。
 *
 * - 普通行（items/rooms/spaces/categories/tags）按 updatedAt LWW 合并；物品写入时只覆盖协议字段，
 *   本地独有列（brand/unit/条码/照片/尺寸等）予以保留。
 * - 物品的自定义字段值与标签集合随物品一起 LWW（远端视为该物品的完整集合）。
 * - categoryFields 无时间戳：push 全量覆盖，pull 时以服务端快照为准（删除随之生效）。
 * - transactions 按 id 去重追加（Room insertTransactions = IGNORE）。
 * - lastSince/serverUrl/token/deviceId 存 DataStore Preferences。
 */
@Singleton
class SyncRepository @Inject constructor(
    private val dao: NashiDao,
    @ApplicationContext private val appContext: Context,
) {
    private object Keys {
        val LAST_SINCE = longPreferencesKey("last_since")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val SERVER_URL = stringPreferencesKey("server_url")
        val TOKEN = stringPreferencesKey("device_token")
        val FAMILY_ID = stringPreferencesKey("family_id")
        val ADMIN_TOKEN = stringPreferencesKey("admin_token")
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val http = HttpClient(Android) {
        expectSuccess = true
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
        }
    }

    suspend fun lastSince(): Long =
        appContext.syncPrefs.data.map { it[Keys.LAST_SINCE] ?: 0L }.first()

    suspend fun deviceId(): String {
        val store = appContext.syncPrefs.data.first()
        store[Keys.DEVICE_ID]?.takeIf { it.isNotBlank() }?.let { return it }
        val fresh = UUID.randomUUID().toString()
        appContext.syncPrefs.edit { it[Keys.DEVICE_ID] = fresh }
        return fresh
    }

    /** 绑定自托管服务端（扫码/手工输入 invite 后调用）。 */
    suspend fun configure(serverUrl: String, token: String) {
        appContext.syncPrefs.edit {
            it[Keys.SERVER_URL] = serverUrl.trimEnd('/')
            it[Keys.TOKEN] = token
        }
    }

    fun settingsFlow(): Flow<SyncSettings> =
        appContext.syncPrefs.data.map { it.toSettings() }

    private fun Preferences.toSettings() = SyncSettings(
        serverUrl = this[Keys.SERVER_URL] ?: "",
        token = this[Keys.TOKEN] ?: "",
        familyId = this[Keys.FAMILY_ID] ?: "",
        adminToken = this[Keys.ADMIN_TOKEN] ?: "",
        deviceId = this[Keys.DEVICE_ID] ?: "",
    )

    /** 在服务端建家庭, 本机以 adminToken 直接参与同步。 */
    suspend fun initFamily(serverUrl: String): FamilyInitResp {
        val base = normalizeUrl(serverUrl)
        val resp: FamilyInitResp = http.post("$base/family/init") {
            contentType(ContentType.Application.Json)
        }.body()
        configure(base, resp.adminToken)
        appContext.syncPrefs.edit {
            it[Keys.FAMILY_ID] = resp.familyId
            it[Keys.ADMIN_TOKEN] = resp.adminToken
            it[Keys.LAST_SINCE] = 0L
        }
        return resp
    }

    /** 管理员生成邀请码; 非管理员会由服务端返回 401。 */
    suspend fun createInvite(): String {
        val prefs = appContext.syncPrefs.data.first()
        val settings = prefs.toSettings()
        require(settings.serverUrl.isNotBlank()) { "请先初始化家庭或填写服务器地址" }
        require(settings.isAdmin) { "当前设备不是家庭管理员, 无法生成邀请码" }
        val resp: InviteResp = http.post("${settings.serverUrl}/family/invite") {
            contentType(ContentType.Application.Json)
            setBody(InviteBody(settings.familyId, settings.adminToken))
        }.body()
        return resp.inviteCode
    }

    /** 用邀请码加入已有家庭, 保存 deviceToken 后即可同步。 */
    suspend fun joinFamily(serverUrl: String, inviteCode: String): JoinResp {
        val base = normalizeUrl(serverUrl)
        val code = inviteCode.trim()
        require(code.isNotBlank()) { "邀请码不能为空" }
        val resp: JoinResp = http.post("$base/family/join") {
            contentType(ContentType.Application.Json)
            setBody(JoinBody(code, deviceId()))
        }.body()
        configure(base, resp.deviceToken)
        appContext.syncPrefs.edit {
            it[Keys.FAMILY_ID] = resp.familyId
            it[Keys.ADMIN_TOKEN] = ""
            it[Keys.LAST_SINCE] = 0L
        }
        return resp
    }

    /** 设置页"立即同步"。 */
    suspend fun syncNow(): SyncResult = syncOnce()

    /** 管理页: 本家庭已加入设备与有效邀请码(仅管理员可用)。 */
    suspend fun members(): MembersResp {
        val s = settings()
        require(s.serverUrl.isNotBlank()) { "请先初始化家庭或填写服务器地址" }
        require(s.isAdmin) { "当前设备不是家庭管理员" }
        return http.get("${s.serverUrl}/family/members") { bearerAuth(s.adminToken) }.body()
    }

    suspend fun revokeInvite(code: String): Boolean =
        revoke { RevokeBody(familyId = it.familyId, adminToken = it.adminToken, inviteCode = code) }

    suspend fun revokeDevice(deviceId: String): Boolean =
        revoke { RevokeBody(familyId = it.familyId, adminToken = it.adminToken, deviceId = deviceId) }

    private suspend fun revoke(body: (SyncSettings) -> RevokeBody): Boolean {
        val s = settings()
        require(s.isAdmin) { "当前设备不是家庭管理员" }
        val resp: OkResp = http.post("${s.serverUrl}/family/revoke") {
            contentType(ContentType.Application.Json)
            setBody(body(s))
        }.body()
        return resp.ok
    }

    private suspend fun settings(): SyncSettings = appContext.syncPrefs.data.first().toSettings()

    suspend fun syncOnce(): SyncResult {
        val prefs = appContext.syncPrefs.data.first()
        val base = (prefs[Keys.SERVER_URL] ?: "").trimEnd('/')
        if (base.isBlank()) return SyncResult.Skipped("server not configured")
        val token = prefs[Keys.TOKEN] ?: ""
        val since0 = prefs[Keys.LAST_SINCE] ?: 0L
        val device = deviceId()
        return try {
            // ---- push: 本地 dirty（updatedAt > lastSince）----
            val dirtyItems = dao.dirtyItems(since0)
            val allRooms = dao.allRooms()
            val dirtyRooms = allRooms.filter { it.updatedAt > since0 }
            val dirtySpaces = mutableListOf<StorageSpace>()
            for (r in allRooms) {
                dirtySpaces += dao.spacesOf(r.id).filter { it.updatedAt > since0 }
            }
            val dirtyTx = dao.dirtyTransactions(since0)
            val valuesByItem = dao.allFieldValues().groupBy({ it.itemId }, { it.key to it.value })
            val tagsByItem = dao.allItemTags().groupBy({ it.itemId }, { it.tagId })
            val pushBody = PushBody(
                changes = ChangeSet(
                    items = dirtyItems.map {
                        it.toDto(device, valuesByItem[it.id].orEmpty().toMap(), tagsByItem[it.id].orEmpty())
                    },
                    rooms = dirtyRooms.map { it.toDto() },
                    spaces = dirtySpaces.map { it.toDto() },
                    transactions = dirtyTx.map { it.toDto() },
                    categories = dao.everyCategory().filter { it.updatedAt > since0 }.map { it.toDto() },
                    tags = dao.everyTag().filter { it.updatedAt > since0 }.map { it.toDto() },
                    categoryFields = dao.allCategoryFields().map { it.toDto() },
                ),
                deviceId = device,
            )
            val pushResp: PushResp = http.post("$base/sync/push") {
                contentType(ContentType.Application.Json)
                if (token.isNotBlank()) bearerAuth(token)
                header("X-Device-Id", device)
                setBody(pushBody)
            }.body()

            // ---- pull：循环至 nextSince 收敛 ----
            var since = since0
            var pulled = 0
            var guard = 0
            while (guard++ < 25) {
                val page: PullResp = http.get("$base/sync/pull") {
                    if (token.isNotBlank()) bearerAuth(token)
                    header("X-Device-Id", device)
                    parameter("since", since)
                    parameter("limit", 500)
                }.body()
                applyChanges(page.changes)
                pulled += page.changes.size()
                if (page.changes.isEmpty() || page.nextSince <= since) {
                    since = maxOf(since, page.nextSince)
                    break
                }
                since = page.nextSince
            }
            appContext.syncPrefs.edit { it[Keys.LAST_SINCE] = since }
            SyncResult.Success(
                pushed = pushBody.changes.size(),
                pulled = pulled,
                conflicts = pushResp.conflicts.size,
            )
        } catch (e: Exception) {
            SyncResult.Failed(e.message ?: e.toString())
        }
    }

    // ---- pull 合并：普通行 LWW，tx 去重追加 ----
    private suspend fun applyChanges(changes: ChangeSet) {
        if (changes.items.isNotEmpty()) {
            val purged = appContext.purgedItemIds()
            val accepted = mutableListOf<ItemDto>()
            val merged = changes.items.mapNotNull { dto ->
                when (val decision = decideItemMerge(dto, dao.itemById(dto.id), purged)) {
                    ItemMerge.Skip -> null
                    is ItemMerge.Insert -> {
                        accepted += dto
                        decision.item
                    }
                    is ItemMerge.Overwrite -> {
                        accepted += dto
                        decision.item
                    }
                }
            }
            if (merged.isNotEmpty()) dao.upsertItems(merged)
            for (dto in accepted) {
                // 远端集合即完整集合：先清本地再写入，才能传播删除
                dao.deleteFieldValuesOf(dto.id)
                dao.deleteItemTagsOf(dto.id)
                if (dto.fieldValues.isNotEmpty()) {
                    dao.upsertFieldValues(dto.fieldValues.map { (k, v) -> ItemFieldValue(dto.id, k, v) })
                }
                if (dto.tagIds.isNotEmpty()) {
                    dao.upsertItemTags(dto.tagIds.map { ItemTag(dto.id, it) })
                }
            }
        }
        if (changes.rooms.isNotEmpty()) {
            val localById = dao.allRooms().associateBy { it.id }
            val merged = changes.rooms.mapNotNull { dto ->
                val local = localById[dto.id]
                when {
                    local == null -> HomeRoom(id = dto.id, name = dto.name, updatedAt = dto.updatedAt)
                    dto.updatedAt >= local.updatedAt -> local.copy(name = dto.name, updatedAt = dto.updatedAt)
                    else -> null
                }
            }
            if (merged.isNotEmpty()) dao.upsertRooms(merged)
        }
        if (changes.spaces.isNotEmpty()) {
            val byRoom = changes.spaces.groupBy { it.roomId }
            val merged = mutableListOf<StorageSpace>()
            for ((roomId, dtos) in byRoom) {
                val localById = dao.spacesOf(roomId).associateBy { it.id }
                for (dto in dtos) {
                    val local = localById[dto.id]
                    when {
                        local == null -> merged += StorageSpace(
                            id = dto.id, roomId = dto.roomId, name = dto.name,
                            updatedAt = dto.updatedAt,
                        )
                        dto.updatedAt >= local.updatedAt ->
                            merged += local.copy(roomId = dto.roomId, name = dto.name, updatedAt = dto.updatedAt)
                    }
                }
            }
            if (merged.isNotEmpty()) dao.upsertSpaces(merged)
        }
        if (changes.transactions.isNotEmpty()) {
            dao.insertTransactions(
                changes.transactions.map {
                    StockTransaction(it.id, it.itemId, it.delta, it.kind, it.createdAt, it.deviceId)
                }
            )
        }
        if (changes.categories.isNotEmpty()) {
            val localById = dao.everyCategory().associateBy { it.id }
            val merged = changes.categories.mapNotNull { dto ->
                val local = localById[dto.id]
                when {
                    local == null -> Category(
                        id = dto.id, name = dto.name, icon = dto.icon,
                        updatedAt = dto.updatedAt, updatedBy = dto.updatedBy,
                        version = dto.version, isDeleted = dto.isDeleted,
                    )
                    dto.updatedAt >= local.updatedAt -> local.copy(
                        name = dto.name, icon = dto.icon, updatedAt = dto.updatedAt,
                        updatedBy = dto.updatedBy, version = dto.version, isDeleted = dto.isDeleted,
                    )
                    else -> null
                }
            }
            if (merged.isNotEmpty()) dao.upsertCategories(merged)
        }
        if (changes.tags.isNotEmpty()) {
            val localById = dao.everyTag().associateBy { it.id }
            val merged = changes.tags.mapNotNull { dto ->
                val local = localById[dto.id]
                when {
                    local == null -> Tag(
                        id = dto.id, name = dto.name, updatedAt = dto.updatedAt,
                        updatedBy = dto.updatedBy, version = dto.version, isDeleted = dto.isDeleted,
                    )
                    dto.updatedAt >= local.updatedAt -> local.copy(
                        name = dto.name, updatedAt = dto.updatedAt,
                        updatedBy = dto.updatedBy, version = dto.version, isDeleted = dto.isDeleted,
                    )
                    else -> null
                }
            }
            if (merged.isNotEmpty()) dao.upsertTags(merged)
        }
        if (changes.categoryFields.isNotEmpty()) {
            dao.clearCategoryFields()
            dao.upsertCategoryFields(
                changes.categoryFields.map {
                    CategoryFieldDef(
                        id = it.id, categoryId = it.categoryId, key = it.key, label = it.label,
                        type = it.type, required = it.required, optionsCsv = it.optionsCsv,
                    )
                }
            )
        }
    }
}

// ---- entity -> DTO ----
private fun Category.toDto() = CategoryDto(
    id = id, name = name, icon = icon, updatedAt = updatedAt,
    updatedBy = updatedBy, version = version, isDeleted = isDeleted,
)

private fun Tag.toDto() = TagDto(
    id = id, name = name, updatedAt = updatedAt,
    updatedBy = updatedBy, version = version, isDeleted = isDeleted,
)

private fun CategoryFieldDef.toDto() = FieldDefDto(
    id = id, categoryId = categoryId, key = key, label = label,
    type = type, required = required, optionsCsv = optionsCsv,
)

private fun HomeRoom.toDto() = RoomDto(id = id, name = name, updatedAt = updatedAt)

private fun StorageSpace.toDto() = SpaceDto(id = id, roomId = roomId, name = name, updatedAt = updatedAt)

private fun StockTransaction.toDto() = TxDto(
    id = id, itemId = itemId, delta = delta, kind = kind,
    createdAt = createdAt, deviceId = deviceId,
)

private fun normalizeUrl(raw: String): String {
    val trimmed = raw.trim().trimEnd('/')
    require(trimmed.isNotBlank()) { "服务器地址不能为空" }
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "http://$trimmed"
}
