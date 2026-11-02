package com.nashi.core.io

import com.nashi.core.data.NashiDao
import com.nashi.core.model.HomeRoom
import com.nashi.core.model.Item
import com.nashi.core.model.StorageSpace
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// 导入导出: JSON 全量备份 + CSV(兼容 GameSorter games/shelves 双表)
// games 列: Name,Width,Height,Depth,ThisSideUp,LockInPlace,Bookcase,Column,Shelf,X,Y,Z
// shelves 列: Bookcase,Columns,Width,Depth,MaxOverhang,ShelfName,Height,AllowOverhang,Locked
object NashiIo {
    // shelves 独有列(games 表永远不含), 用于按完整表头签名判别
    private val SHELVES_ONLY_COLUMNS = setOf("columns", "maxoverhang", "shelfname", "allowoverhang", "locked")
    // games 独有列(shelves 表永远不含)
    private val GAMES_ONLY_COLUMNS = setOf("name", "thissideup", "lockinplace", "column", "shelf", "x", "y", "z")

    /**
     * 按表头签名判别导入类型(不再单列 contains("Bookcase") —— GAMES_HEADER 自身就含 Bookcase 列)。
     * 1. 命中任一 shelves 独有列 -> shelves (真实 GameSorter shelves 表走这里)
     * 2. 命中任一 games 独有列 -> games (自家 games 导出走这里, 优先于 Bookcase 判断)
     * 3. 两类独有列都没有(只剩 Bookcase/Width/Height/Depth 这类公共列) -> 沿用 Bookcase 兜底
     */
    fun parseGameSorterCsv(header: List<String>): String {
        val cols = header.map { it.trim().lowercase() }.toSet()
        if (cols.any { it in SHELVES_ONLY_COLUMNS }) return "shelves"
        if (cols.any { it in GAMES_ONLY_COLUMNS }) return "games"
        return if (cols.any { it == "bookcase" }) "shelves" else "games"
    }

    // ---------- JSON 全量备份 ----------

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = true; prettyPrint = true }

    @Serializable
    data class BackupV1(
        val version: Int = 1,
        val categories: List<CatDto> = emptyList(),
        val categoryFields: List<FieldDefDto> = emptyList(),
        val items: List<ItemDto> = emptyList(),
        val rooms: List<RoomDto> = emptyList(),
        val spaces: List<SpaceDto> = emptyList(),
        val tags: List<TagDto> = emptyList(),
        val itemTags: List<ItemTagDto> = emptyList(),
        val fieldValues: List<FieldValueDto> = emptyList(),
        val transactions: List<TxDto> = emptyList(),
    )

    @Serializable
    data class CatDto(
        val id: String, val name: String, val icon: String = "",
        val updatedAt: Long = 0L, val updatedBy: String = "",
        val version: Int = 1, val isDeleted: Boolean = false,
    )

    @Serializable
    data class FieldDefDto(
        val id: String, val categoryId: String, val key: String, val label: String,
        val type: String, val required: Boolean = false, val optionsCsv: String = "",
    )

    @Serializable
    data class ItemDto(
        val id: String, val name: String, val categoryId: String,
        val brand: String = "", val quantity: Double = 1.0, val unit: String = "件",
        val barcode: String = "", val qrPayload: String = "",
        val photoPathsCsv: String = "",
        val roomId: String = "", val spaceId: String = "",
        val x: Float = 0f, val y: Float = 0f, val z: Float = 0f,
        val width: Float = 0f, val height: Float = 0f, val depth: Float = 0f,
        val inTime: Long = 0L, val expiryDate: Long? = null, val minStock: Double = 0.0,
        val updatedAt: Long = 0L, val updatedBy: String = "",
        val version: Int = 1, val isDeleted: Boolean = false,
    )

    @Serializable
    data class RoomDto(
        val id: String, val name: String,
        val width: Float = 0f, val height: Float = 0f, val depth: Float = 0f,
        val updatedAt: Long = 0L, val updatedBy: String = "",
        val version: Int = 1, val isDeleted: Boolean = false,
    )

    @Serializable
    data class SpaceDto(
        val id: String, val roomId: String, val name: String,
        val type: String = "shelf",
        val width: Float = 0f, val depth: Float = 0f, val shelfHeight: Float = 0f,
        val columns: Int = 1, val maxOverhang: Float = 10f,
        val allowOverhang: Boolean = true, val locked: Boolean = false,
        val positionIndex: Int = 0,
        val updatedAt: Long = 0L, val updatedBy: String = "",
        val version: Int = 1, val isDeleted: Boolean = false,
    )

    @Serializable
    data class TagDto(
        val id: String, val name: String,
        val updatedAt: Long = 0L, val updatedBy: String = "",
        val version: Int = 1, val isDeleted: Boolean = false,
    )

    @Serializable
    data class ItemTagDto(val itemId: String, val tagId: String)

    @Serializable
    data class FieldValueDto(val itemId: String, val key: String, val value: String)

    @Serializable
    data class TxDto(
        val id: String, val itemId: String, val delta: Double, val kind: String,
        val createdAt: Long = 0L, val deviceId: String = "",
    )

    suspend fun exportJson(dao: NashiDao): String {
        val b = BackupV1(
            version = 1,
            categories = dao.allCategories().map {
                CatDto(it.id, it.name, it.icon, it.updatedAt, it.updatedBy, it.version, it.isDeleted)
            },
            categoryFields = dao.allCategoryFields().map {
                FieldDefDto(it.id, it.categoryId, it.key, it.label, it.type, it.required, it.optionsCsv)
            },
            items = dao.allItems().map {
                ItemDto(
                    it.id, it.name, it.categoryId, it.brand, it.quantity, it.unit,
                    it.barcode, it.qrPayload, it.photoPathsCsv, it.roomId, it.spaceId,
                    it.x, it.y, it.z, it.width, it.height, it.depth,
                    it.inTime, it.expiryDate, it.minStock,
                    it.updatedAt, it.updatedBy, it.version, it.isDeleted,
                )
            },
            rooms = dao.allRooms().map {
                RoomDto(it.id, it.name, it.width, it.height, it.depth, it.updatedAt, it.updatedBy, it.version, it.isDeleted)
            },
            spaces = dao.allSpaces().map {
                SpaceDto(
                    it.id, it.roomId, it.name, it.type, it.width, it.depth, it.shelfHeight,
                    it.columns, it.maxOverhang, it.allowOverhang, it.locked, it.positionIndex,
                    it.updatedAt, it.updatedBy, it.version, it.isDeleted,
                )
            },
            tags = dao.allTags().map {
                TagDto(it.id, it.name, it.updatedAt, it.updatedBy, it.version, it.isDeleted)
            },
            itemTags = dao.allItemTags().map { ItemTagDto(it.itemId, it.tagId) },
            fieldValues = dao.allFieldValues().map { FieldValueDto(it.itemId, it.key, it.value) },
            transactions = dao.allTransactions().map {
                TxDto(it.id, it.itemId, it.delta, it.kind, it.createdAt, it.deviceId)
            },
        )
        return json.encodeToString(b)
    }

    /**
     * 回写入库, 返回**真实新增行数**(主键已存在的行会被 REPLACE 覆盖, 但不计入返回值).
     * 可重复导入: 同一份数据第二次导入返回 0。
     * 注: DAO 的 upsert* 返回 Unit(Room @Insert 未暴露 rowid), 因此“新增”用导入前的全表 id 快照差集计算。
     * 已知局限: allCategories()/allTags() 带 isDeleted=0 过滤, 同 id 的软删除行会被算作“新增”(实际是覆盖)。
     */
    suspend fun importJson(dao: NashiDao, text: String): Int {
        val b = json.decodeFromString<BackupV1>(text)
        var n = 0
        if (b.categories.isNotEmpty()) {
            val existing = dao.allCategories().map { it.id }.toSet()
            n += b.categories.map { it.id }.distinct().count { it !in existing }
            dao.upsertCategories(b.categories.map {
                com.nashi.core.model.Category(it.id, it.name, it.icon, it.updatedAt, it.updatedBy, it.version, it.isDeleted)
            })
        }
        if (b.categoryFields.isNotEmpty()) {
            val existing = dao.allCategoryFields().map { it.id }.toSet()
            n += b.categoryFields.map { it.id }.distinct().count { it !in existing }
            dao.upsertCategoryFields(b.categoryFields.map {
                com.nashi.core.model.CategoryFieldDef(it.id, it.categoryId, it.key, it.label, it.type, it.required, it.optionsCsv)
            })
        }
        if (b.rooms.isNotEmpty()) {
            val existing = dao.allRooms().map { it.id }.toSet()
            n += b.rooms.map { it.id }.distinct().count { it !in existing }
            dao.upsertRooms(b.rooms.map {
                HomeRoom(it.id, it.name, it.width, it.height, it.depth, it.updatedAt, it.updatedBy, it.version, it.isDeleted)
            })
        }
        if (b.spaces.isNotEmpty()) {
            val existing = dao.allSpaces().map { it.id }.toSet()
            n += b.spaces.map { it.id }.distinct().count { it !in existing }
            dao.upsertSpaces(b.spaces.map {
                StorageSpace(
                    it.id, it.roomId, it.name, it.type, it.width, it.depth, it.shelfHeight,
                    it.columns, it.maxOverhang, it.allowOverhang, it.locked, it.positionIndex,
                    it.updatedAt, it.updatedBy, it.version, it.isDeleted,
                )
            })
        }
        if (b.tags.isNotEmpty()) {
            val existing = dao.allTags().map { it.id }.toSet()
            n += b.tags.map { it.id }.distinct().count { it !in existing }
            dao.upsertTags(b.tags.map {
                com.nashi.core.model.Tag(it.id, it.name, it.updatedAt, it.updatedBy, it.version, it.isDeleted)
            })
        }
        if (b.items.isNotEmpty()) {
            val existing = dao.allItems().map { it.id }.toSet()
            n += b.items.map { it.id }.distinct().count { it !in existing }
            dao.upsertItems(b.items.map {
                Item(
                    id = it.id, name = it.name, categoryId = it.categoryId, brand = it.brand,
                    quantity = it.quantity, unit = it.unit, barcode = it.barcode, qrPayload = it.qrPayload,
                    photoPathsCsv = it.photoPathsCsv, roomId = it.roomId, spaceId = it.spaceId,
                    x = it.x, y = it.y, z = it.z, width = it.width, height = it.height, depth = it.depth,
                    inTime = it.inTime, expiryDate = it.expiryDate, minStock = it.minStock,
                    updatedAt = it.updatedAt, updatedBy = it.updatedBy, version = it.version, isDeleted = it.isDeleted,
                )
            })
        }
        if (b.itemTags.isNotEmpty()) {
            val existing = dao.allItemTags().map { it.itemId to it.tagId }.toSet()
            n += b.itemTags.map { it.itemId to it.tagId }.distinct().count { it !in existing }
            dao.upsertItemTags(b.itemTags.map { com.nashi.core.model.ItemTag(it.itemId, it.tagId) })
        }
        if (b.fieldValues.isNotEmpty()) {
            val existing = dao.allFieldValues().map { it.itemId to it.key }.toSet()
            n += b.fieldValues.map { it.itemId to it.key }.distinct().count { it !in existing }
            dao.upsertFieldValues(b.fieldValues.map {
                com.nashi.core.model.ItemFieldValue(it.itemId, it.key, it.value)
            })
        }
        if (b.transactions.isNotEmpty()) {
            val existing = dao.allTransactions().map { it.id }.toSet()
            n += b.transactions.map { it.id }.distinct().count { it !in existing }
            dao.insertTransactions(b.transactions.map {
                com.nashi.core.model.StockTransaction(it.id, it.itemId, it.delta, it.kind, it.createdAt, it.deviceId)
            })
        }
        return n
    }

    // ---------- GameSorter CSV ----------

    private val GAMES_HEADER = listOf("Name", "Width", "Height", "Depth", "ThisSideUp", "LockInPlace", "Bookcase", "Column", "Shelf", "X", "Y", "Z")
    private val SHELVES_HEADER = listOf("Bookcase", "Columns", "Width", "Depth", "MaxOverhang", "ShelfName", "Height", "AllowOverhang", "Locked")

    private fun esc(v: Any?): String {
        val s = v?.toString() ?: ""
        return if (s.contains(',') || s.contains('"') || s.contains('\n') || s.contains('\r')) {
            "\"" + s.replace("\"", "\"\"") + "\""
        } else {
            s
        }
    }

    /** 物品表 -> games CSV 字符串. Bookcase/Shelf 取空间名(找不到回退 id). */
    suspend fun exportGameSorterCsv(dao: NashiDao): String {
        val spaceNames = dao.allSpaces().associate { it.id to it.name }
        val sb = StringBuilder()
        sb.append(GAMES_HEADER.joinToString(",")).append('\n')
        for (it in dao.allItems().filter { !it.isDeleted }) {
            val bookcase = spaceNames[it.spaceId] ?: it.spaceId
            sb.append(
                listOf(
                    esc(it.name), esc(it.width), esc(it.height), esc(it.depth),
                    esc(1), esc(0), esc(bookcase), esc(0), esc(bookcase),
                    esc(it.x), esc(it.y), esc(it.z),
                ).joinToString(","),
            ).append('\n')
        }
        return sb.toString()
    }

    /** 空间表 -> shelves CSV 字符串. */
    suspend fun exportGameSorterShelvesCsv(dao: NashiDao): String {
        val sb = StringBuilder()
        sb.append(SHELVES_HEADER.joinToString(",")).append('\n')
        for (sp in dao.allSpaces().filter { !it.isDeleted }) {
            sb.append(
                listOf(
                    esc(sp.name), esc(sp.columns), esc(sp.width), esc(sp.depth),
                    esc(sp.maxOverhang), esc(sp.name), esc(sp.shelfHeight),
                    esc(if (sp.allowOverhang) 1 else 0), esc(if (sp.locked) 1 else 0),
                ).joinToString(","),
            ).append('\n')
        }
        return sb.toString()
    }

    /** 按 RFC4180 子集切分一行(处理引号/转义引号). */
    fun splitCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        cur.append('"')
                        i += 2
                    } else {
                        inQuotes = false
                        i++
                    }
                } else {
                    cur.append(ch)
                    i++
                }
            } else {
                when (ch) {
                    '"' -> { inQuotes = true; i++ }
                    ',' -> { out.add(cur.toString()); cur.clear(); i++ }
                    else -> { cur.append(ch); i++ }
                }
            }
        }
        out.add(cur.toString())
        return out
    }

    /** 整份 CSV 文本 -> (表头, 行映射表). 空行跳过. */
    fun parseCsvTable(text: String): Pair<List<String>, List<Map<String, String>>> {
        val lines = text.split('\n').map { it.trimEnd('\r') }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList<String>() to emptyList()
        val header = splitCsvLine(lines[0]).map { it.trim() }
        val rows = lines.drop(1).map { line ->
            val cells = splitCsvLine(line)
            header.mapIndexed { idx, h -> h to (cells.getOrNull(idx)?.trim() ?: "") }.toMap()
        }
        return header to rows
    }

    private fun fnum(s: String?): Float = s?.toFloatOrNull() ?: 0f
    private fun inum(s: String?): Int = s?.toIntOrNull() ?: 0

    /**
     * GameSorter 行 -> Item/Space 回写, 返回导入行数.
     * kind 来自 parseGameSorterCsv(header).
     * games 行按 Bookcase(空间名)匹配空间, 找不到则 spaceId/roomId 留空;
     * 每行新建 Item(uuid, 默认分类 cat-other), 追加导入(非幂等).
     * shelves 行按空间名匹配已有空间更新尺寸, 找不到则在首个房间(无房间则建 room-import)下新建.
     */
    suspend fun importGameSorter(dao: NashiDao, kind: String, rows: List<Map<String, String>>): Int {
        val now = System.currentTimeMillis()
        if (kind == "shelves") {
            val spaces = dao.allSpaces()
            val byName = spaces.associateBy { it.name }
            var roomId = dao.allRooms().firstOrNull()?.id
            if (roomId == null) {
                roomId = "room-import"
                dao.upsertRooms(listOf(HomeRoom(roomId, "导入", updatedAt = now)))
            }
            val target = roomId
            var n = 0
            for (r in rows) {
                val name = r["Bookcase"]?.takeIf { it.isNotBlank() } ?: continue
                val old = byName[name]
                val sp = StorageSpace(
                    id = old?.id ?: UUID.randomUUID().toString(),
                    roomId = old?.roomId ?: target,
                    name = r["ShelfName"]?.takeIf { it.isNotBlank() } ?: name,
                    width = fnum(r["Width"]), depth = fnum(r["Depth"]),
                    shelfHeight = fnum(r["Height"]),
                    columns = inum(r["Columns"]).takeIf { it > 0 } ?: (old?.columns ?: 1),
                    maxOverhang = r["MaxOverhang"]?.toFloatOrNull() ?: (old?.maxOverhang ?: 10f),
                    allowOverhang = r["AllowOverhang"] !in listOf("0", "false", "FALSE", "False"),
                    locked = r["Locked"] in listOf("1", "true", "TRUE", "True"),
                    positionIndex = old?.positionIndex ?: 0,
                    updatedAt = now,
                )
                dao.upsertSpaces(listOf(sp))
                n++
            }
            return n
        }
        // games
        val spaces = dao.allSpaces()
        val spaceByName = spaces.associateBy { it.name }
        var n = 0
        for (r in rows) {
            val name = r["Name"]?.takeIf { it.isNotBlank() } ?: continue
            val sp = spaceByName[r["Bookcase"]]
            val item = Item(
                id = UUID.randomUUID().toString(),
                name = name,
                categoryId = "cat-other",
                width = fnum(r["Width"]), height = fnum(r["Height"]), depth = fnum(r["Depth"]),
                roomId = sp?.roomId ?: "", spaceId = sp?.id ?: "",
                x = fnum(r["X"]), y = fnum(r["Y"]), z = fnum(r["Z"]),
                inTime = now, updatedAt = now,
            )
            dao.upsertItems(listOf(item))
            n++
        }
        return n
    }

    // ---------- 照片备份包: backup.json + photos/ (可选 AES-256-GCM 加密) ----------

    private const val BACKUP_ENTRY = "backup.json"
    private const val PHOTO_PREFIX = "photos/"
    private const val MANIFEST_ENTRY = "manifest.json"
    private const val ENC_SUFFIX = ".enc"
    private const val KDF_ITERATIONS = 120_000
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12
    private const val SALT_BYTES = 16

    @Serializable
    data class EncManifest(
        val format: String = "nashi-backup",
        val version: Int = 2,
        val cipher: String = "AES-256-GCM",
        val kdf: String = "PBKDF2WithHmacSHA256",
        val iterations: Int = KDF_ITERATIONS,
        val salt: String,
    )

    // 只取文件名: 既抹平不同设备的绝对路径, 也挡住 zip 条目里的 ../ 路径穿越。
    private fun photoBaseName(path: String): String =
        path.substringAfterLast('/').substringAfterLast('\\')

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKey {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(raw, "AES")
    }

    /** 每个加密条目 = 12 字节随机 IV + GCM 密文(含 16 字节认证标签)。 */
    private fun writeEncrypted(dst: OutputStream, key: SecretKey, body: ByteArray) {
        val iv = ByteArray(GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        dst.write(iv)
        dst.write(cipher.doFinal(body))
    }

    private fun readDecrypted(src: InputStream, key: SecretKey): ByteArray {
        val all = src.readBytes()
        require(all.size > GCM_IV_BYTES + GCM_TAG_BITS / 8) { "加密条目已损坏" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, all, 0, GCM_IV_BYTES))
        return try {
            cipher.doFinal(all, GCM_IV_BYTES, all.size - GCM_IV_BYTES)
        } catch (e: GeneralSecurityException) {
            throw IllegalArgumentException("备份口令错误或备份包已损坏")
        }
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, body: ByteArray, key: SecretKey?) {
        zip.putNextEntry(ZipEntry(name))
        if (key == null) zip.write(body) else writeEncrypted(zip, key, body)
        zip.closeEntry()
    }

    /** 导出 ZIP, 返回打包的照片数量; 照片文件缺失时跳过(不阻断备份)。password 非空时整体加密。 */
    suspend fun exportZip(
        dao: NashiDao,
        photoDir: File?,
        out: OutputStream,
        password: String? = null,
    ): Int {
        val text = exportJson(dao)
        val photos = dao.allItems()
            .flatMap { it.photoPathsCsv.split('|') }
            .filter { it.isNotBlank() }
            .map { it to photoBaseName(it) }
            .distinctBy { it.second }
        val pwd = password?.takeIf { it.isNotEmpty() }
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val key = pwd?.let { deriveKey(it, salt, KDF_ITERATIONS) }
        var packed = 0
        ZipOutputStream(BufferedOutputStream(out)).use { zip ->
            if (key != null) {
                // 清单固定放包首: 导入时必须先拿到 salt 才能解密后续条目。
                val manifest = EncManifest(salt = Base64.getEncoder().encodeToString(salt))
                writeEntry(zip, MANIFEST_ENTRY, json.encodeToString(manifest).toByteArray(Charsets.UTF_8), null)
            }
            writeEntry(
                zip,
                if (key == null) BACKUP_ENTRY else BACKUP_ENTRY + ENC_SUFFIX,
                text.toByteArray(Charsets.UTF_8),
                key,
            )
            for ((path, name) in photos) {
                if (name.isBlank()) continue
                val src = File(path).takeIf { it.isFile }
                    ?: photoDir?.let { File(it, name) }?.takeIf { it.isFile }
                    ?: continue
                if (key == null) {
                    zip.putNextEntry(ZipEntry(PHOTO_PREFIX + name))
                    src.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                } else {
                    // 照片只有几百 KB, 整块读入以换取"每条目独立认证"的简单实现。
                    writeEntry(zip, PHOTO_PREFIX + name + ENC_SUFFIX, src.readBytes(), key)
                }
                packed++
            }
        }
        return packed
    }

    /**
     * 导入 ZIP: 还原 backup.json, 照片落到 photoDir 并重写物品里的路径。返回新增行数。
     * 加密包需传 password; 未传或口令错误时抛异常, 且不写数据库。
     */
    suspend fun importZip(
        dao: NashiDao,
        photoDir: File?,
        input: InputStream,
        password: String? = null,
    ): Int {
        var jsonBytes: ByteArray? = null
        var key: SecretKey? = null
        val restored = mutableMapOf<String, String>()
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val entryName = entry.name
                if (!entry.isDirectory) {
                    when {
                        entryName == MANIFEST_ENTRY -> {
                            val manifest = json.decodeFromString<EncManifest>(zip.readBytes().toString(Charsets.UTF_8))
                            val pwd = password?.takeIf { it.isNotBlank() } ?: throw BackupPasswordRequired()
                            key = deriveKey(pwd, Base64.getDecoder().decode(manifest.salt), manifest.iterations)
                        }
                        entryName == BACKUP_ENTRY -> jsonBytes = zip.readBytes()
                        entryName == BACKUP_ENTRY + ENC_SUFFIX -> jsonBytes =
                            readDecrypted(zip, key ?: throw IllegalArgumentException("加密包缺少 $MANIFEST_ENTRY"))
                        entryName.startsWith(PHOTO_PREFIX) && photoDir != null -> {
                            val encrypted = entryName.endsWith(ENC_SUFFIX)
                            val name = photoBaseName(
                                entryName.removePrefix(PHOTO_PREFIX)
                                    .removeSuffix(if (encrypted) ENC_SUFFIX else "")
                            )
                            if (name.isNotBlank()) {
                                val bytes = if (encrypted) {
                                    readDecrypted(zip, key ?: throw IllegalArgumentException("加密包缺少 $MANIFEST_ENTRY"))
                                } else {
                                    zip.readBytes()
                                }
                                photoDir.mkdirs()
                                val dst = File(photoDir, name)
                                dst.writeBytes(bytes)
                                restored[name] = dst.absolutePath
                            }
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        val text = jsonBytes?.toString(Charsets.UTF_8)
            ?: throw IllegalArgumentException("备份包缺少 $BACKUP_ENTRY")
        val n = importJson(dao, text)
        if (restored.isNotEmpty()) {
            val patched = dao.allItems().mapNotNull { item ->
                val paths = item.photoPathsCsv.split('|').filter { it.isNotBlank() }
                val rewritten = paths.map { restored[photoBaseName(it)] ?: it }
                if (rewritten == paths) null else item.copy(photoPathsCsv = rewritten.joinToString("|"))
            }
            if (patched.isNotEmpty()) dao.upsertItems(patched)
        }
        return n
    }
}

/** 加密备份包需要口令才能继续导入, UI 捕获后弹出输入框再重试。 */
class BackupPasswordRequired : Exception("备份包已加密, 请输入口令")
