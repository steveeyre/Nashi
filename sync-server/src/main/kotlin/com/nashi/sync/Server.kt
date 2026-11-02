package com.nashi.sync

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// ---------------------------------------------------------------------------
// DTO：与 app/src/main/java/com/nashi/core/sync/SyncProtocol.kt 逐字段对齐。
// 服务端扩展字段（均有默认值，老客户端 ignoreUnknownKeys 可忽略）：
//   - PullResp.serverTime：客户端可用它校准 nextSince，避免时钟漂移漏拉；
//   - ConflictDto.serverUpdatedAt/reason：LWW 输者回执展示用。
// ---------------------------------------------------------------------------
@Serializable data class ItemDto(
    val id: String, val name: String, val categoryId: String, val quantity: Double,
    val roomId: String = "", val spaceId: String = "", val updatedAt: Long,
    val updatedBy: String = "", val version: Int = 1, val isDeleted: Boolean = false,
    val brand: String = "", val unit: String = "件", val barcode: String = "",
    val inTime: Long = 0L, val expiryDate: Long? = null, val minStock: Double = 0.0,
    val width: Float = 0f, val height: Float = 0f, val depth: Float = 0f,
    val fieldValues: Map<String, String> = emptyMap(),
    val tagIds: List<String> = emptyList(),
)
@Serializable data class RoomDto(val id: String, val name: String, val updatedAt: Long)
@Serializable data class SpaceDto(val id: String, val roomId: String, val name: String, val updatedAt: Long)
@Serializable data class TxDto(
    val id: String, val itemId: String, val delta: Double, val kind: String,
    val createdAt: Long, val deviceId: String = "",
)
@Serializable data class CategoryDto(
    val id: String, val name: String, val icon: String = "", val updatedAt: Long,
    val updatedBy: String = "", val version: Int = 1, val isDeleted: Boolean = false,
)
@Serializable data class TagDto(
    val id: String, val name: String, val updatedAt: Long,
    val updatedBy: String = "", val version: Int = 1, val isDeleted: Boolean = false,
)
@Serializable data class FieldDefDto(
    val id: String, val categoryId: String, val key: String, val label: String,
    val type: String, val required: Boolean = false, val optionsCsv: String = "",
)
@Serializable data class ChangeSet(
    val items: List<ItemDto> = emptyList(),
    val rooms: List<RoomDto> = emptyList(),
    val spaces: List<SpaceDto> = emptyList(),
    val transactions: List<TxDto> = emptyList(),
    val categories: List<CategoryDto> = emptyList(),
    val tags: List<TagDto> = emptyList(),
    val categoryFields: List<FieldDefDto> = emptyList(),
)
@Serializable data class PullResp(val changes: ChangeSet, val nextSince: Long, val serverTime: Long = 0L)
@Serializable data class PushBody(val changes: ChangeSet, val deviceId: String = "")
@Serializable data class PushResp(val conflicts: List<ConflictDto> = emptyList())
@Serializable data class ConflictDto(val kind: String, val id: String, val serverUpdatedAt: Long, val reason: String = "")
@Serializable data class FamilyInitResp(val familyId: String, val adminToken: String)
@Serializable data class InviteBody(val familyId: String, val adminToken: String = "")
@Serializable data class InviteResp(val inviteCode: String, val expiresAt: Long = 0L)
@Serializable data class JoinBody(val inviteCode: String, val deviceId: String)
@Serializable data class JoinResp(val familyId: String, val deviceToken: String)
@Serializable data class RevokeBody(
    val familyId: String, val adminToken: String = "",
    val inviteCode: String = "", val deviceId: String = "",
)
@Serializable data class OkResp(val ok: Boolean = true)
@Serializable data class DeviceInfoDto(val deviceId: String)
@Serializable data class InviteInfoDto(
    val code: String, val expiresAt: Long,
    val used: Boolean = false, val revoked: Boolean = false,
)
@Serializable data class MembersResp(
    val devices: List<DeviceInfoDto> = emptyList(),
    val invites: List<InviteInfoDto> = emptyList(),
)
@Serializable data class HealthResp(val status: String = "ok")
@Serializable data class ErrorResp(val error: String)

// ---------------------------------------------------------------------------
// 存储：运行时在内存，每次变更后把整份状态原子写盘到 DATA_DIR/state.json，
// 进程重启按快照恢复家庭/token/全部数据。Postgres 后端（schema.sql）尚未接线。
// ---------------------------------------------------------------------------
class FamilyStore {
    val items = ConcurrentHashMap<String, ItemDto>()
    val rooms = ConcurrentHashMap<String, RoomDto>()
    val spaces = ConcurrentHashMap<String, SpaceDto>()
    val txs = ConcurrentHashMap<String, TxDto>()
    val categories = ConcurrentHashMap<String, CategoryDto>()
    val tags = ConcurrentHashMap<String, TagDto>()
    val fieldDefs = ConcurrentHashMap<String, FieldDefDto>()
}

@Serializable data class FamilySnapshot(
    val id: String,
    val adminTokenHash: String = "",
    // 1.x 快照字段: 明文 token 与邀请码, 只在加载时升级, 写盘时永远为空
    val adminToken: String = "",
    val inviteCodes: List<String> = emptyList(),
    // token 的 SHA-256 摘要 -> deviceId
    val devices: Map<String, String> = emptyMap(),
    val items: List<ItemDto> = emptyList(),
    val rooms: List<RoomDto> = emptyList(),
    val spaces: List<SpaceDto> = emptyList(),
    val txs: List<TxDto> = emptyList(),
    val categories: List<CategoryDto> = emptyList(),
    val tags: List<TagDto> = emptyList(),
    val fieldDefs: List<FieldDefDto> = emptyList(),
)

@Serializable data class ServerSnapshot(
    val families: List<FamilySnapshot> = emptyList(),
    val inviteGrants: List<Invite> = emptyList(),
    // 1.x 字段: code -> familyId, 加载时升级成带过期的 Invite
    val invites: Map<String, String> = emptyMap(),
)

data class Family(
    val id: String,
    val adminTokenHash: String,
    // token 的 SHA-256 摘要 -> deviceId
    val devices: ConcurrentHashMap<String, String> = ConcurrentHashMap(),
    val store: FamilyStore = FamilyStore(),
) {
    fun snapshot(): FamilySnapshot = FamilySnapshot(
        id = id,
        adminTokenHash = adminTokenHash,
        devices = devices.toMap(),
        items = store.items.values.toList(),
        rooms = store.rooms.values.toList(),
        spaces = store.spaces.values.toList(),
        txs = store.txs.values.toList(),
        categories = store.categories.values.toList(),
        tags = store.tags.values.toList(),
        fieldDefs = store.fieldDefs.values.toList(),
    )

    companion object {
        fun from(s: FamilySnapshot): Family {
            val (adminTokenHash, devices) = upgradeLegacyTokens(s.adminToken, s.adminTokenHash, s.devices)
            return Family(
                id = s.id,
                adminTokenHash = adminTokenHash,
                devices = ConcurrentHashMap<String, String>().apply { putAll(devices) },
                store = FamilyStore().apply {
                    s.items.forEach { items[it.id] = it }
                    s.rooms.forEach { rooms[it.id] = it }
                    s.spaces.forEach { spaces[it.id] = it }
                    s.txs.forEach { txs[it.id] = it }
                    s.categories.forEach { categories[it.id] = it }
                    s.tags.forEach { tags[it.id] = it }
                    s.fieldDefs.forEach { fieldDefs[it.id] = it }
                },
            )
        }
    }
}

object Registry {
    val families = ConcurrentHashMap<String, Family>()
    // inviteCode -> 邀请记录(带有效期/已用/已撤销)
    val invites = ConcurrentHashMap<String, Invite>()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    @Volatile private var stateFile: Path? = null

    /** 启动时指定数据目录并恢复上次快照。 */
    fun init(dataDir: Path) {
        Files.createDirectories(dataDir)
        stateFile = dataDir.resolve("state.json")
        load()
    }

    fun familyOf(token: String?): Family? {
        if (token.isNullOrBlank()) return null
        val h = hashToken(token)
        return families.values.firstOrNull { it.adminTokenHash == h || it.devices.containsKey(h) }
    }

    /** 管理操作只认管理员 token, 设备 token 无权邀请或撤销。 */
    fun adminOf(familyId: String, adminToken: String): Family? =
        families[familyId]?.takeIf {
            it.adminTokenHash.isNotBlank() && it.adminTokenHash == hashToken(adminToken)
        }

    /** 按管理员 token 直接定位家庭, 供 GET /family/members 使用。 */
    fun adminFamilyOf(token: String?): Family? {
        if (token.isNullOrBlank()) return null
        val h = hashToken(token)
        return families.values.firstOrNull { it.adminTokenHash.isNotBlank() && it.adminTokenHash == h }
    }

    /** 状态变更后调用: 整份快照写临时文件再原子替换, 崩溃不会留下半个文件。 */
    @Synchronized
    fun persist() {
        val file = stateFile ?: return
        val snapshot = ServerSnapshot(families.values.map { it.snapshot() }, invites.values.toList())
        val tmp = file.resolveSibling("state.json.tmp")
        Files.writeString(tmp, json.encodeToString(snapshot))
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun load() {
        val file = stateFile ?: return
        if (!Files.exists(file)) return
        val snapshot = json.decodeFromString<ServerSnapshot>(Files.readString(file))
        val now = System.currentTimeMillis()
        snapshot.families.forEach { s ->
            families[s.id] = Family.from(s)
            // 1.x 的邀请码没有有效期, 升级时给一个 TTL 窗口
            s.inviteCodes.forEach { code ->
                invites.putIfAbsent(code, Invite(code, s.id, now + INVITE_TTL_MS))
            }
        }
        snapshot.inviteGrants.forEach { invites[it.code] = it }
        snapshot.invites.forEach { (code, familyId) ->
            invites.putIfAbsent(code, Invite(code, familyId, now + INVITE_TTL_MS))
        }
        println("nashi-sync: restored ${snapshot.families.size} family(ies) from $file")
    }
}

/** 邀请码有效期, 默认 24 小时; 可用 INVITE_TTL_HOURS 覆盖。 */
val INVITE_TTL_MS: Long =
    (System.getenv("INVITE_TTL_HOURS")?.toLongOrNull() ?: 24L).coerceAtLeast(1L) * 60 * 60 * 1000

fun newId(): String = UUID.randomUUID().toString().replace("-", "")

private suspend fun persistState() = withContext(Dispatchers.IO) { Registry.persist() }

fun Application.syncModule() {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
    routing {
        get("/health") { call.respond(HealthResp()) }

        post("/family/init") {
            val familyId = newId()
            val adminToken = "adm_" + newId()
            Registry.families[familyId] = Family(familyId, hashToken(adminToken))
            persistState()
            call.respond(FamilyInitResp(familyId, adminToken))
        }

        post("/family/invite") {
            val body = call.receive<InviteBody>()
            val fam = Registry.adminOf(body.familyId, body.adminToken)
            if (fam == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResp("bad familyId/adminToken"))
                return@post
            }
            val now = System.currentTimeMillis()
            Registry.invites.entries.removeIf { it.value.expiresAt <= now }
            val invite = Invite(
                code = "INV-" + newId().take(8).uppercase(),
                familyId = fam.id,
                expiresAt = now + INVITE_TTL_MS,
            )
            Registry.invites[invite.code] = invite
            persistState()
            call.respond(InviteResp(invite.code, invite.expiresAt))
        }

        post("/family/join") {
            val body = call.receive<JoinBody>()
            val now = System.currentTimeMillis()
            val invite = Registry.invites[body.inviteCode]
            if (!inviteUsable(invite, now)) {
                call.respond(HttpStatusCode.NotFound, ErrorResp("inviteCode expired/used/revoked"))
                return@post
            }
            val fam = Registry.families[invite!!.familyId]
            if (fam == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResp("family gone"))
                return@post
            }
            // 一次性: 用掉即置 used, 旧码不能反复换 token
            Registry.invites[invite.code] = invite.copy(used = true)
            val token = "dev_" + newId()
            fam.devices[hashToken(token)] = body.deviceId
            persistState()
            call.respond(JoinResp(fam.id, token))
        }

        // 管理页: 列出已加入设备与本家庭有效邀请码(仅管理员可见)。
        get("/family/members") {
            val token = call.request.header("Authorization")?.removePrefix("Bearer ")?.trim()
            val fam = Registry.adminFamilyOf(token)
            if (fam == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResp("admin token required"))
                return@get
            }
            val now = System.currentTimeMillis()
            call.respond(
                MembersResp(
                    devices = memberDevices(fam.devices).map { DeviceInfoDto(it) },
                    invites = visibleInvites(Registry.invites.values, fam.id, now).map {
                        InviteInfoDto(it.code, it.expiresAt, it.used, it.revoked)
                    },
                )
            )
        }

        // 管理员撤销邀请码或踢掉某台设备(deviceId 即客户端本地生成的设备号)。
        post("/family/revoke") {
            val body = call.receive<RevokeBody>()
            val fam = Registry.adminOf(body.familyId, body.adminToken)
            if (fam == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResp("bad familyId/adminToken"))
                return@post
            }
            var changed = false
            if (body.inviteCode.isNotBlank()) {
                val invite = Registry.invites[body.inviteCode]
                if (invite != null && invite.familyId == fam.id) {
                    Registry.invites[invite.code] = invite.copy(revoked = true)
                    changed = true
                }
            }
            if (body.deviceId.isNotBlank()) {
                val before = fam.devices.size
                fam.devices.entries.removeIf { it.value == body.deviceId }
                if (fam.devices.size < before) changed = true
            }
            if (changed) persistState()
            call.respond(OkResp(changed))
        }

        get("/sync/pull") {
            val token = call.request.header("Authorization")?.removePrefix("Bearer ")?.trim()
            val fam = Registry.familyOf(token)
            if (fam == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResp("missing/invalid token"))
                return@get
            }
            val since = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L
            val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 500).coerceIn(1, 1000)
            val page = buildPullPage(fam.store, since, limit)
            call.respond(
                PullResp(page.changes, nextSince = page.nextSince, serverTime = System.currentTimeMillis())
            )
        }

        post("/sync/push") {
            val token = call.request.header("Authorization")?.removePrefix("Bearer ")?.trim()
            val fam = Registry.familyOf(token)
            if (fam == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResp("missing/invalid token"))
                return@post
            }
            val body = call.receive<PushBody>()
            val conflicts = applyPush(fam.store, body.changes)
            persistState()
            call.respond(PushResp(conflicts))
        }
    }
}

fun main() {
    val dataDir = Paths.get(System.getenv("DATA_DIR") ?: "./data")
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    Registry.init(dataDir)
    println("nashi-sync: data dir ${dataDir.toAbsolutePath()}, port $port")
    embeddedServer(Netty, port = port) { syncModule() }.start(wait = true)
}
