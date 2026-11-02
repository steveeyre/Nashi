package com.nashi.core.io

import com.nashi.core.data.NashiDao
import com.nashi.core.model.Category
import com.nashi.core.model.CategoryFieldDef
import com.nashi.core.model.HomeRoom
import com.nashi.core.model.Item
import com.nashi.core.model.ItemFieldValue
import com.nashi.core.model.ItemTag
import com.nashi.core.model.StockTransaction
import com.nashi.core.model.StorageSpace
import com.nashi.core.model.Tag
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NashiIo 纯 JVM 测试(生产源码无 android.* 依赖, 用 mockk 假 Dao 即可).
 *
 * 真实行为要点(对照 NashiIo.kt):
 * - splitCsvLine (L236): RFC4180 子集; 引号内 `""` 转义为一个 `"`; 结尾总会 add 当前缓冲
 * - parseCsvTable (L269): 按 '\n' 切分 -> 去尾部 '\r' -> 丢弃 isNotBlank()==false 的行; 表头与单元格均 trim
 * - parseGameSorterCsv (L17): 按表头**签名**判别 —— 命中 shelves 独有列(Columns/MaxOverhang/ShelfName/AllowOverhang/Locked)
 *   即 shelves; 否则命中 games 独有列(Name/ThisSideUp/LockInPlace/X/Y/Z...) 即 games; 只剩公共列时用 Bookcase 兜底
 * - exportJson (L93) / importJson: JSON 全量备份, 字段 1:1 映射; importJson 返回**真实新增行数**
 */
class NashiIoTest {

    private val plainJson = Json { ignoreUnknownKeys = true }

    // ------------------------------------------------------------------
    // splitCsvLine
    // ------------------------------------------------------------------

    @Test
    fun `splitCsvLine 基本切分不含引号`() {
        assertEquals(listOf("a", "b", "c"), NashiIo.splitCsvLine("a,b,c"))
    }

    @Test
    fun `splitCsvLine 引号内的逗号不前切`() {
        assertEquals(listOf("a,b", "c"), NashiIo.splitCsvLine("\"a,b\",c"))
    }

    @Test
    fun `splitCsvLine 双引号转义为一个引号`() {
        assertEquals(listOf("say \"hi\"", "x"), NashiIo.splitCsvLine("\"say \"\"hi\"\"\",x"))
    }

    @Test
    fun `splitCsvLine 空字段与空行`() {
        assertEquals(listOf("a", "", "c"), NashiIo.splitCsvLine("a,,c"))
        // 记录真实行为: 空串输入返回一个空字段(不是空列表)
        assertEquals(listOf(""), NashiIo.splitCsvLine(""))
        // 引号包裹的空字段
        assertEquals(listOf("", "b"), NashiIo.splitCsvLine("\"\",b"))
    }

    // ------------------------------------------------------------------
    // parseCsvTable
    // ------------------------------------------------------------------

    @Test
    fun `parseCsvTable 表头去空格且跳过空行`() {
        val text = " Name , Width \n A , 1 \n\n B , 2 \n"
        val (header, rows) = NashiIo.parseCsvTable(text)
        assertEquals(listOf("Name", "Width"), header)
        assertEquals(2, rows.size)
        assertEquals(mapOf("Name" to "A", "Width" to "1"), rows[0])
        assertEquals(mapOf("Name" to "B", "Width" to "2"), rows[1])
    }

    @Test
    fun `parseCsvTable 缺列补空串`() {
        val (header, rows) = NashiIo.parseCsvTable("A,B,C\n1,2\n")
        assertEquals(listOf("A", "B", "C"), header)
        assertEquals(mapOf("A" to "1", "B" to "2", "C" to ""), rows[0])
    }

    @Test
    fun `parseCsvTable 空文本返回空表头空行`() {
        val (header, rows) = NashiIo.parseCsvTable("")
        assertTrue(header.isEmpty())
        assertTrue(rows.isEmpty())

        val (h2, r2) = NashiIo.parseCsvTable("\n\n   \n")
        assertTrue(h2.isEmpty())
        assertTrue(r2.isEmpty())
    }

    @Test
    fun `parseCsvTable 处理 CRLF 与带引号单元格`() {
        val (header, rows) = NashiIo.parseCsvTable("Name,Note\r\n\"锤, 大\",\"带\"\"引号\"\"\"\r\n")
        assertEquals(listOf("Name", "Note"), header)
        assertEquals(1, rows.size)
        assertEquals("锤, 大", rows[0]["Name"])
        assertEquals("带\"引号\"", rows[0]["Note"])
    }

    // ------------------------------------------------------------------
    // parseGameSorterCsv
    // ------------------------------------------------------------------

    @Test
    fun `parseGameSorterCsv 含 Bookcase 表头判为 shelves`() {
        val shelvesHeader = listOf(
            "Bookcase", "Columns", "Width", "Depth", "MaxOverhang",
            "ShelfName", "Height", "AllowOverhang", "Locked",
        )
        assertEquals("shelves", NashiIo.parseGameSorterCsv(shelvesHeader))
    }

    @Test
    fun `parseGameSorterCsv 不含 Bookcase 表头判为 games`() {
        assertEquals("games", NashiIo.parseGameSorterCsv(listOf("Name", "Width", "Height")))
        assertNotEquals("shelves", NashiIo.parseGameSorterCsv(emptyList()))
        assertEquals("games", NashiIo.parseGameSorterCsv(emptyList()))
    }

    /**
     * P1 回归: 自家 games 导出也带 Bookcase 列(旧实现用单列 contains 判别, 导致被当成 shelves).
     * 修复后必须按完整表头签名判为 games。
     */
    @Test
    fun `自家导出的 games CSV 再导入必须仍判为 games`() {
        val dao = mockk<NashiDao>(relaxed = true).also { d ->
            coEvery { d.allItems() } returns listOf(sampleItem())
            coEvery { d.allSpaces() } returns listOf(sampleSpace())
        }
        val csv = runBlocking { NashiIo.exportGameSorterCsv(dao) }
        val (header, rows) = NashiIo.parseCsvTable(csv)
        assertEquals(1, rows.size)
        assertTrue("GAMES_HEADER 确实含 Bookcase 列(旧实现误判的根因)", header.contains("Bookcase"))
        assertEquals("games", NashiIo.parseGameSorterCsv(header))
    }

    @Test
    fun `自家导出的 shelves CSV 再导入必须仍判为 shelves`() {
        val dao = mockk<NashiDao>(relaxed = true).also { d ->
            coEvery { d.allSpaces() } returns listOf(sampleSpace())
        }
        val csv = runBlocking { NashiIo.exportGameSorterShelvesCsv(dao) }
        val (header, rows) = NashiIo.parseCsvTable(csv)
        assertEquals(1, rows.size)
        assertEquals("shelves", NashiIo.parseGameSorterCsv(header))
    }

    @Test
    fun `真实 GameSorter shelves 表仍能识别`() {
        // 真实导出可能缺 AllowOverhang/Locked, 列顺序也未必固定
        val real = listOf("Bookcase", "Columns", "Width", "Depth", "MaxOverhang", "ShelfName", "Height")
        assertEquals("shelves", NashiIo.parseGameSorterCsv(real))
        // 顺序打乱 + 大小写混写
        assertEquals("shelves", NashiIo.parseGameSorterCsv(listOf("Height", "bookcase", "MAXOVERHANG", "Width")))
        // 只剩公共列时才走 Bookcase 兜底
        assertEquals("shelves", NashiIo.parseGameSorterCsv(listOf("Bookcase", "Width", "Height", "Depth")))
    }

    /**
     * P1 后果回归: games CSV 再导入必须走 games 分支写 items, 不能去 upsertSpaces 写货架。
     */
    @Test
    fun `games CSV 再导入只写物品不写空间`() = runBlocking {
        val exportDao = mockk<NashiDao>(relaxed = true).also { d ->
            coEvery { d.allItems() } returns listOf(sampleItem())
            coEvery { d.allSpaces() } returns listOf(sampleSpace())
        }
        val csv = NashiIo.exportGameSorterCsv(exportDao)
        val (header, rows) = NashiIo.parseCsvTable(csv)

        val writeDao = mockk<NashiDao>(relaxed = true).also { d ->
            coEvery { d.allItems() } returns emptyList()
            coEvery { d.allSpaces() } returns listOf(sampleSpace())
            coEvery { d.allRooms() } returns listOf(sampleRoom())
        }
        val itemSlot = slot<List<Item>>()
        coEvery { writeDao.upsertItems(capture(itemSlot)) } returns Unit

        val n = NashiIo.importGameSorter(writeDao, NashiIo.parseGameSorterCsv(header), rows)

        assertEquals(1, n)
        assertEquals("物品名应从 games 行写入", listOf("锤子"), itemSlot.captured.map { it.name })
        coVerify(exactly = 0) { writeDao.upsertSpaces(any()) }
    }

    /**
     * P1 反向后果回归: shelves CSV 再导入必须走 shelves 分支写空间, 不能写 items。
     * 用于堵住「判别写反」这类对称错误 —— games 用例只证明了「不写空间」, 这条证明「不写物品」。
     */
    @Test
    fun `shelves CSV 再导入只写空间不写物品`() = runBlocking {
        val exportDao = mockk<NashiDao>(relaxed = true).also { d ->
            coEvery { d.allSpaces() } returns listOf(sampleSpace())
        }
        val csv = NashiIo.exportGameSorterShelvesCsv(exportDao)
        val (header, rows) = NashiIo.parseCsvTable(csv)
        assertEquals(1, rows.size)
        assertEquals("shelves", NashiIo.parseGameSorterCsv(header))

        val db = FakeDb()
        val dao = fakeDao(db)

        val n = NashiIo.importGameSorter(dao, NashiIo.parseGameSorterCsv(header), rows)

        assertEquals(1, n)
        assertTrue("shelves 行不得写物品表", db.items.isEmpty())
        assertEquals(1, db.spaces.size)
        val sp = db.spaces[0]
        assertEquals("空间名应来自 ShelfName", "A架", sp.name)
        assertEquals(80f, sp.width, 0f)
        assertEquals(40f, sp.depth, 0f)
        assertEquals(30f, sp.shelfHeight, 0f)
        assertEquals("Columns 列应回写", 2, sp.columns)
        assertEquals("MaxOverhang 列应回写", 5f, sp.maxOverhang, 0f)
        assertEquals("AllowOverhang=0 应回写为 false", false, sp.allowOverhang)
        assertEquals("Locked=1 应回写为 true", true, sp.locked)
        assertEquals("无房间时应落到 room-import", "room-import", sp.roomId)
    }

    // ------------------------------------------------------------------
    // exportJson / importJson
    // ------------------------------------------------------------------

    private fun sampleCategory() = Category(
        id = "c1", name = "工具", icon = "icon-x",
        updatedAt = 1_000L, updatedBy = "dev-a", version = 2, isDeleted = false,
    )

    private fun sampleItem() = Item(
        id = "i1", name = "锤子", categoryId = "c1",
        brand = "Acme", quantity = 3.0, unit = "把",
        barcode = "6901234567890", qrPayload = "qr:i1", photoPathsCsv = "/a.jpg,/b.jpg",
        roomId = "r1", spaceId = "sp1",
        x = 1f, y = 2f, z = 3f, width = 10f, height = 11f, depth = 12f,
        inTime = 111L, expiryDate = 999L, minStock = 1.5,
        updatedAt = 222L, updatedBy = "dev-a", version = 3, isDeleted = false,
    )

    private fun sampleRoom() = HomeRoom(
        id = "r1", name = "客厅", width = 300f, height = 250f, depth = 400f,
        updatedAt = 333L, updatedBy = "dev-a", version = 1, isDeleted = false,
    )

    private fun sampleSpace() = StorageSpace(
        id = "sp1", roomId = "r1", name = "A架", type = "shelf",
        width = 80f, depth = 40f, shelfHeight = 30f,
        columns = 2, maxOverhang = 5f, allowOverhang = false, locked = true, positionIndex = 3,
        updatedAt = 444L, updatedBy = "dev-a", version = 4, isDeleted = false,
    )

    private fun sampleTag() = Tag(
        id = "t1", name = "易碎",
        updatedAt = 555L, updatedBy = "dev-a", version = 1, isDeleted = false,
    )

    private fun sampleItemTag() = ItemTag(itemId = "i1", tagId = "t1")

    private fun sampleFieldDef() = CategoryFieldDef(
        id = "f1", categoryId = "c1", key = "volume", label = "容量",
        type = "number", required = true, optionsCsv = "",
    )

    private fun sampleFieldValue() = ItemFieldValue(itemId = "i1", key = "volume", value = "1.5L")

    private fun sampleTx() = StockTransaction(
        id = "tx1", itemId = "i1", delta = -1.0, kind = "out",
        createdAt = 666L, deviceId = "dev-1",
    )

    private fun emptyDao(): NashiDao = mockk<NashiDao>(relaxed = true).also { d ->
        coEvery { d.allCategories() } returns emptyList()
        coEvery { d.allItems() } returns emptyList()
        coEvery { d.allRooms() } returns emptyList()
        coEvery { d.allSpaces() } returns emptyList()
        coEvery { d.allTags() } returns emptyList()
        coEvery { d.allItemTags() } returns emptyList()
        coEvery { d.allTransactions() } returns emptyList()
        coEvery { d.allCategoryFields() } returns emptyList()
        coEvery { d.allFieldValues() } returns emptyList()
    }

    private fun readDao(): NashiDao = mockk<NashiDao>(relaxed = true).also { d ->
        coEvery { d.allCategories() } returns listOf(sampleCategory())
        coEvery { d.allItems() } returns listOf(sampleItem())
        coEvery { d.allRooms() } returns listOf(sampleRoom())
        coEvery { d.allSpaces() } returns listOf(sampleSpace())
        coEvery { d.allTags() } returns listOf(sampleTag())
        coEvery { d.allItemTags() } returns listOf(sampleItemTag())
        coEvery { d.allTransactions() } returns listOf(sampleTx())
        coEvery { d.allCategoryFields() } returns emptyList()
        coEvery { d.allFieldValues() } returns listOf(sampleFieldValue())
    }

    /** 有状态内存假 Dao: upsert* 按主键 REPLACE, insertTransactions 按主键 IGNORE。 */
    private class FakeDb {
        val categories = mutableListOf<Category>()
        val items = mutableListOf<Item>()
        val rooms = mutableListOf<HomeRoom>()
        val spaces = mutableListOf<StorageSpace>()
        val tags = mutableListOf<Tag>()
        val itemTags = mutableListOf<ItemTag>()
        val transactions = mutableListOf<StockTransaction>()
        val categoryFields = mutableListOf<CategoryFieldDef>()
        val fieldValues = mutableListOf<ItemFieldValue>()
    }

    /** mockk 实现的假 Dao: 读走内存快照, 写按 Room 的真实冲突策略落地, 用于验证幂等/新增计数。 */
    private fun fakeDao(db: FakeDb): NashiDao = mockk<NashiDao>(relaxed = true).also { d ->
        coEvery { d.allCategories() } answers { db.categories.toList() }
        coEvery { d.allItems() } answers { db.items.toList() }
        coEvery { d.allRooms() } answers { db.rooms.toList() }
        coEvery { d.allSpaces() } answers { db.spaces.toList() }
        coEvery { d.allTags() } answers { db.tags.toList() }
        coEvery { d.allItemTags() } answers { db.itemTags.toList() }
        coEvery { d.allTransactions() } answers { db.transactions.toList() }
        coEvery { d.allCategoryFields() } answers { db.categoryFields.toList() }
        coEvery { d.allFieldValues() } answers { db.fieldValues.toList() }

        coEvery { d.upsertCategories(any()) } answers {
            firstArg<List<Category>>().forEach { v -> db.categories.removeAll { it.id == v.id }; db.categories.add(v) }
        }
        coEvery { d.upsertItems(any()) } answers {
            firstArg<List<Item>>().forEach { v -> db.items.removeAll { it.id == v.id }; db.items.add(v) }
        }
        coEvery { d.upsertRooms(any()) } answers {
            firstArg<List<HomeRoom>>().forEach { v -> db.rooms.removeAll { it.id == v.id }; db.rooms.add(v) }
        }
        coEvery { d.upsertSpaces(any()) } answers {
            firstArg<List<StorageSpace>>().forEach { v -> db.spaces.removeAll { it.id == v.id }; db.spaces.add(v) }
        }
        coEvery { d.upsertTags(any()) } answers {
            firstArg<List<Tag>>().forEach { v -> db.tags.removeAll { it.id == v.id }; db.tags.add(v) }
        }
        coEvery { d.upsertItemTags(any()) } answers {
            firstArg<List<ItemTag>>().forEach { v ->
                db.itemTags.removeAll { it.itemId == v.itemId && it.tagId == v.tagId }
                db.itemTags.add(v)
            }
        }
        coEvery { d.insertTransactions(any()) } answers {
            firstArg<List<StockTransaction>>().forEach { v ->
                if (db.transactions.none { it.id == v.id }) db.transactions.add(v)
            }
        }
        coEvery { d.upsertCategoryFields(any()) } answers {
            firstArg<List<CategoryFieldDef>>().forEach { v ->
                db.categoryFields.removeAll { it.id == v.id }; db.categoryFields.add(v)
            }
        }
        coEvery { d.upsertFieldValues(any()) } answers {
            firstArg<List<ItemFieldValue>>().forEach { v ->
                db.fieldValues.removeAll { it.itemId == v.itemId && it.key == v.key }
                db.fieldValues.add(v)
            }
        }
    }

    @Test
    fun `exportJson 空库输出可解析且 version 为 1`() = runBlocking {
        val json = NashiIo.exportJson(emptyDao())
        val b = plainJson.decodeFromString<NashiIo.BackupV1>(json)
        assertEquals(1, b.version)
        assertTrue(b.categories.isEmpty())
        assertTrue(b.items.isEmpty())
        assertTrue(b.rooms.isEmpty())
        assertTrue(b.spaces.isEmpty())
        assertTrue(b.tags.isEmpty())
        assertTrue(b.itemTags.isEmpty())
        assertTrue(b.transactions.isEmpty())
        assertTrue(b.categoryFields.isEmpty())
    }

    @Test
    fun `exportJson 输出各集合尺寸与源数据一致`() = runBlocking {
        val json = NashiIo.exportJson(readDao())
        val b = plainJson.decodeFromString<NashiIo.BackupV1>(json)
        assertEquals(1, b.version)
        assertEquals(1, b.categories.size)
        assertEquals(1, b.items.size)
        assertEquals(1, b.rooms.size)
        assertEquals(1, b.spaces.size)
        assertEquals(1, b.tags.size)
        assertEquals(1, b.itemTags.size)
        assertEquals(1, b.transactions.size)
        assertTrue(b.categoryFields.isEmpty())
    }

    @Test
    fun `exportJson 到 importJson 往返 字段逐一还原且返回写入行数`() = runBlocking {
        val json = NashiIo.exportJson(readDao())

        val writeDao = mockk<NashiDao>(relaxed = true)
        val catSlot = slot<List<Category>>()
        val roomSlot = slot<List<HomeRoom>>()
        val spaceSlot = slot<List<StorageSpace>>()
        val tagSlot = slot<List<Tag>>()
        val itemSlot = slot<List<Item>>()
        val itemTagSlot = slot<List<ItemTag>>()
        val fvSlot = slot<List<ItemFieldValue>>()
        val txSlot = slot<List<StockTransaction>>()
        coEvery { writeDao.upsertCategories(capture(catSlot)) } returns Unit
        coEvery { writeDao.upsertRooms(capture(roomSlot)) } returns Unit
        coEvery { writeDao.upsertSpaces(capture(spaceSlot)) } returns Unit
        coEvery { writeDao.upsertTags(capture(tagSlot)) } returns Unit
        coEvery { writeDao.upsertItems(capture(itemSlot)) } returns Unit
        coEvery { writeDao.upsertItemTags(capture(itemTagSlot)) } returns Unit
        coEvery { writeDao.upsertFieldValues(capture(fvSlot)) } returns Unit
        coEvery { writeDao.insertTransactions(capture(txSlot)) } returns Unit

        val n = NashiIo.importJson(writeDao, json)

        // 8 个集合各 1 条
        assertEquals(8, n)
        // 写回模型字段与原始一致(实体是 data class, 全字段相等)
        assertEquals(listOf(sampleCategory()), catSlot.captured)
        assertEquals(listOf(sampleRoom()), roomSlot.captured)
        assertEquals(listOf(sampleSpace()), spaceSlot.captured)
        assertEquals(listOf(sampleTag()), tagSlot.captured)
        assertEquals(listOf(sampleItem()), itemSlot.captured)
        assertEquals(listOf(sampleItemTag()), itemTagSlot.captured)
        assertEquals(listOf(sampleFieldValue()), fvSlot.captured)
        assertEquals(listOf(sampleTx()), txSlot.captured)

        coVerify(exactly = 1) {
            writeDao.upsertCategories(any())
            writeDao.upsertRooms(any())
            writeDao.upsertSpaces(any())
            writeDao.upsertTags(any())
            writeDao.upsertItems(any())
            writeDao.upsertItemTags(any())
            writeDao.upsertFieldValues(any())
            writeDao.insertTransactions(any())
        }
    }

    @Test
    fun `importJson 空集合不写库且返回 0`() = runBlocking {
        val dao = mockk<NashiDao>(relaxed = true)
        val n = NashiIo.importJson(dao, "{\"version\":1}")
        assertEquals(0, n)
        coVerify(exactly = 0) {
            dao.upsertCategories(any())
            dao.upsertRooms(any())
            dao.upsertSpaces(any())
            dao.upsertTags(any())
            dao.upsertItems(any())
            dao.upsertItemTags(any())
            dao.upsertFieldValues(any())
            dao.insertTransactions(any())
        }
    }

    /**
     * P3 回归: importJson 返回**真实新增行数** —— 同一份数据第二次导入必须返回 0,
     * 且库内行数不得翻倍。
     */
    @Test
    fun `importJson 同一份数据重复导入第二次新增 0 行`() = runBlocking {
        val json = NashiIo.exportJson(readDao())
        val db = FakeDb()
        val dao = fakeDao(db)

        val first = NashiIo.importJson(dao, json)
        assertEquals("首次导入 8 个集合各 1 条, 全部为新增", 8, first)
        assertEquals(1, db.categories.size)
        assertEquals(1, db.items.size)

        val second = NashiIo.importJson(dao, json)
        assertEquals("第二次导入不得再计入任何新增行", 0, second)
        // 库内行数不得翻倍
        assertEquals(1, db.categories.size)
        assertEquals(1, db.items.size)
        assertEquals(1, db.rooms.size)
        assertEquals(1, db.spaces.size)
        assertEquals(1, db.tags.size)
        assertEquals(1, db.itemTags.size)
        assertEquals(1, db.fieldValues.size)
        assertEquals("transactions 是 IGNORE 语义, 重复 id 不新增", 1, db.transactions.size)
    }

    @Test
    fun `importJson 只统计真正新增的行`() = runBlocking {
        val json = NashiIo.exportJson(readDao())
        // 预先已存在同 id 的物品: 该行会被覆盖但不计入新增
        val db = FakeDb().also { it.items.add(sampleItem()) }
        val dao = fakeDao(db)

        val n = NashiIo.importJson(dao, json)

        assertEquals("8 个集合中 items 已存在, 只应新增 7 行", 7, n)
        assertEquals("物品表仍只有 1 行", 1, db.items.size)
        assertEquals("覆盖写把字段恢复成备份值", listOf(sampleItem()), db.items)
    }

    /**
     * P3 定点回归: transactions 是 `INSERT OR IGNORE`, 同 id 行不会真插入, 因此**不得**计入新增。
     * 旧实现用 `b.transactions.size` 计数, 在这条场景下会返回 7(把未插入的行也算上)。
     */
    @Test
    fun `importJson 已存在的 transaction 不计入新增且不被覆盖`() = runBlocking {
        val json = NashiIo.exportJson(readDao())
        // 库里已有同 id 事务, 但内容不同: IGNORE 语义下应保持原样
        val old = sampleTx().copy(delta = -9.0, deviceId = "dev-old")
        val db = FakeDb().also { it.transactions.add(old) }
        val dao = fakeDao(db)

        val n = NashiIo.importJson(dao, json)

        assertEquals("8 个集合中 transactions 同 id 已存在, 只应新增 7 行", 7, n)
        assertEquals("IGNORE 语义: 不得覆盖已有事务", listOf(old), db.transactions)
    }

    @Test
    fun `exportJson 忽略多余 JSON 字段反向兼容`() = runBlocking {
        val json = NashiIo.exportJson(readDao())
        // 加一个未知字段仍可解析(ignoreUnknownKeys = true)
        val patched = json.replaceFirst("{", "{\"future\":123,")
        val b = plainJson.decodeFromString<NashiIo.BackupV1>(patched)
        assertEquals(1, b.items.size)
    }

    // ------------------------------------------------------------------
    // 以下承接已删除的 NashiIoRealDbRoundTripTest(Robolectric + 内存 Room 真库), 改为纯 JVM:
    // 导出/导入/CSV 分支跑的全部是真实 NashiIo 代码, FakeDb 只负责模拟 Room 的
    // @Insert(onConflict = REPLACE / IGNORE) 落库语义(按主键覆盖/忽略)。
    // ------------------------------------------------------------------

    private fun seededDb(): FakeDb = FakeDb().also { db ->
        db.categories.add(sampleCategory())
        db.items.add(sampleItem())
        db.rooms.add(sampleRoom())
        db.spaces.add(sampleSpace())
        db.tags.add(sampleTag())
        db.itemTags.add(sampleItemTag())
        db.transactions.add(sampleTx())
        db.categoryFields.add(sampleFieldDef())
        db.fieldValues.add(sampleFieldValue())
    }

    @Test
    fun `JSON 往返九张表逐表落库内容与源库一致`() = runBlocking {
        val src = seededDb()
        val json = NashiIo.exportJson(fakeDao(src))

        val dst = FakeDb()
        val n = NashiIo.importJson(fakeDao(dst), json)

        assertEquals("9 个集合各 1 条", 9, n)
        assertEquals(src.categories, dst.categories)
        assertEquals(src.rooms, dst.rooms)
        assertEquals(src.spaces, dst.spaces)
        assertEquals(src.tags, dst.tags)
        assertEquals(src.items, dst.items)
        assertEquals(src.itemTags, dst.itemTags)
        assertEquals(src.transactions, dst.transactions)
        assertEquals("自定义字段定义必须随备份还原", listOf(sampleFieldDef()), dst.categoryFields)
        assertEquals("自定义字段值必须随备份还原", listOf(sampleFieldValue()), dst.fieldValues)
    }

    @Test
    fun `ZIP 备份包含照片且导入后路径重写到本机目录`() = runBlocking {
        val srcPhotoDir = Files.createTempDirectory("nashi-src").toFile()
        val photo = File(srcPhotoDir, "p1.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val src = seededDb().also { db ->
            val items = db.items.map { it.copy(photoPathsCsv = photo.absolutePath) }
            db.items.clear(); db.items.addAll(items)
        }

        val out = ByteArrayOutputStream()
        assertEquals(1, NashiIo.exportZip(fakeDao(src), srcPhotoDir, out))

        val dstPhotoDir = Files.createTempDirectory("nashi-dst").toFile()
        val dst = FakeDb()
        val n = NashiIo.importZip(fakeDao(dst), dstPhotoDir, ByteArrayInputStream(out.toByteArray()))

        assertEquals(9, n)
        val restored = File(dst.items.single().photoPathsCsv)
        assertEquals(File(dstPhotoDir, "p1.jpg").absolutePath, restored.absolutePath)
        assertTrue("照片必须解包到本机目录", restored.isFile)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), restored.readBytes())
    }

    @Test
    fun `ZIP 备份包缺少清单文件时报错且不写入数据库`() = runBlocking {
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("photos/x.jpg"))
            zip.write(byteArrayOf(9))
            zip.closeEntry()
        }
        val dst = FakeDb()
        val err = runCatching {
            NashiIo.importZip(fakeDao(dst), null, ByteArrayInputStream(out.toByteArray()))
        }.exceptionOrNull()

        assertTrue("应报缺少 backup.json", err is IllegalArgumentException)
        assertTrue(dst.items.isEmpty())
    }

    @Test
    fun `加密ZIP不含明文且可用同一口令还原数据与照片`() = runBlocking {
        val srcPhotoDir = Files.createTempDirectory("nashi-enc-src").toFile()
        val photo = File(srcPhotoDir, "p1.jpg").apply { writeBytes(byteArrayOf(7, 8, 9)) }
        val src = seededDb().also { db ->
            val items = db.items.map { it.copy(photoPathsCsv = photo.absolutePath) }
            db.items.clear()
            db.items.addAll(items)
        }
        val out = ByteArrayOutputStream()
        assertEquals(1, NashiIo.exportZip(fakeDao(src), srcPhotoDir, out, password = "nashi-pass"))

        val entries = linkedMapOf<String, ByteArray>()
        java.util.zip.ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                entries[e.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        assertTrue("加密包应有 manifest.json", entries.containsKey("manifest.json"))
        assertFalse("加密包不应出现明文 backup.json", entries.containsKey("backup.json"))
        assertTrue(entries.keys.any { it.startsWith("photos/") })
        entries.forEach { (name, bytes) ->
            val asText = if (name == "manifest.json") bytes.toString(Charsets.UTF_8) else ""
            assertFalse("条目 $name 泄露了物品名", asText.contains("锤子"))
            assertFalse("条目 $name 泄露了分类名", asText.contains("工具"))
        }

        val dstPhotoDir = Files.createTempDirectory("nashi-enc-dst").toFile()
        val dst = FakeDb()
        val n = NashiIo.importZip(
            fakeDao(dst), dstPhotoDir, ByteArrayInputStream(out.toByteArray()), password = "nashi-pass",
        )
        assertEquals(9, n)
        assertEquals("锤子", dst.items.single().name)
        assertArrayEquals(byteArrayOf(7, 8, 9), File(dst.items.single().photoPathsCsv).readBytes())
    }

    @Test
    fun `加密ZIP缺口令时报需要口令且不写库`() = runBlocking {
        val out = ByteArrayOutputStream()
        NashiIo.exportZip(fakeDao(seededDb()), null, out, password = "nashi-pass")

        val dst = FakeDb()
        val err = runCatching {
            NashiIo.importZip(fakeDao(dst), null, ByteArrayInputStream(out.toByteArray()))
        }.exceptionOrNull()

        assertTrue("应抛 BackupPasswordRequired, 实际 $err", err is BackupPasswordRequired)
        assertTrue(dst.items.isEmpty())
    }

    @Test
    fun `加密ZIP口令错误时报错且不写库`() = runBlocking {
        val out = ByteArrayOutputStream()
        NashiIo.exportZip(fakeDao(seededDb()), null, out, password = "nashi-pass")

        val dst = FakeDb()
        val err = runCatching {
            NashiIo.importZip(
                fakeDao(dst), null, ByteArrayInputStream(out.toByteArray()), password = "wrong-pass",
            )
        }.exceptionOrNull()

        assertTrue("错误口令应报非法备份, 实际 $err", err is IllegalArgumentException)
        assertFalse("错误口令不应再提示需要口令", err is BackupPasswordRequired)
        assertTrue(dst.items.isEmpty())
    }

    /**
     * 回归: 自定义字段定义(categoryFields)与字段值(fieldValues)均已纳入备份契约,
     * 导出->导入必须还原; 字段值曾是已知缺口。
     */
    @Test
    fun `备份包含自定义字段定义与字段值并可还原`() = runBlocking {
        val json = NashiIo.exportJson(fakeDao(seededDb()))
        val keys = plainJson.parseToJsonElement(json).jsonObject.keys
        val backedUp = setOf(
            "categories", "categoryFields", "items", "rooms", "spaces",
            "tags", "itemTags", "fieldValues", "transactions",
        )
        val allowed = backedUp + "version"

        assertEquals("九张表都应出现在备份里", 9, backedUp.count { it in keys })
        assertTrue("备份不得含九张表之外的键, 实际=$keys", allowed.containsAll(keys))
        assertTrue("备份 JSON 必须含 categoryFields", keys.contains("categoryFields"))
        assertTrue("备份 JSON 必须含 fieldValues", keys.contains("fieldValues"))

        val b = plainJson.decodeFromString<NashiIo.BackupV1>(json)
        assertEquals(listOf(sampleFieldDef().id), b.categoryFields.map { it.id })
        assertEquals(listOf(sampleFieldValue()), b.fieldValues.map { ItemFieldValue(it.itemId, it.key, it.value) })
    }

    /**
     * P1 后果回归(games 方向): 自家导出的 games CSV 回导入必须走 games 分支——
     * 物品行入库且字段逐一还原, 同时货架尺寸不得被物品行的 W/H/D 覆盖。
     */
    @Test
    fun `games CSV 往返 物品入库且货架不被物品行覆盖`() = runBlocking {
        val db = FakeDb().also { it.items.add(sampleItem()); it.spaces.add(sampleSpace()) }
        val dao = fakeDao(db)
        val csv = NashiIo.exportGameSorterCsv(dao)
        val (header, rows) = NashiIo.parseCsvTable(csv)
        val kind = NashiIo.parseGameSorterCsv(header)

        assertEquals(1, rows.size)
        assertTrue("GAMES_HEADER 含 Bookcase 列(旧版误判的根源, 必须仍然成立)", header.contains("Bookcase"))
        assertEquals("P1: 自家 games 导出必须判为 games", "games", kind)

        val beforeSpace = sampleSpace()
        val n = NashiIo.importGameSorter(dao, kind, rows)

        assertEquals("games 分支应导入 1 行", 1, n)
        assertEquals("物品行必须入库, 原物品保留", 2, db.items.size)
        val imported = db.items.single { it.id != "i1" }
        assertEquals("锤子", imported.name)
        assertEquals("games 行按 Bookcase(空间名)反查空间", "sp1", imported.spaceId)
        assertEquals("r1", imported.roomId)
        assertEquals(10f, imported.width, 0f)
        assertEquals(11f, imported.height, 0f)
        assertEquals(12f, imported.depth, 0f)
        assertEquals(1f, imported.x, 0f)
        assertEquals(2f, imported.y, 0f)
        assertEquals(3f, imported.z, 0f)

        val after = db.spaces.single()
        assertEquals("货架行数量不得变化", 1, db.spaces.size)
        assertEquals("货架 id/房间/尺寸必须原样保留(旧缺陷: 被物品行覆盖)", beforeSpace, after)
        assertEquals("货架宽必须保持 80", 80f, after.width, 0f)
        assertEquals("货架深必须保持 40", 40f, after.depth, 0f)
        assertEquals("货架层高必须保持 30", 30f, after.shelfHeight, 0f)
    }

    /**
     * P1 后果回归(shelves 方向): 自家导出的 shelves CSV 回导入仍需写 spaces 表、
     * 复用原空间 id/roomId, 且**不产生任何 Item**。
     * 与上一条一起锁死两个方向的判别 —— 判别退化时必有一条变红。
     */
    @Test
    fun `shelves CSV 往返 复用原空间 id 与 roomId 且不产生物品`() = runBlocking {
        val db = FakeDb().also { it.items.add(sampleItem()); it.spaces.add(sampleSpace()) }
        val dao = fakeDao(db)
        val csv = NashiIo.exportGameSorterShelvesCsv(dao)
        val (header, rows) = NashiIo.parseCsvTable(csv)

        assertEquals("shelves 表头应判为 shelves", "shelves", NashiIo.parseGameSorterCsv(header))
        assertEquals(1, rows.size)
        assertEquals("80.0", rows[0]["Width"])
        assertEquals("40.0", rows[0]["Depth"])
        assertEquals("30.0", rows[0]["Height"])
        assertEquals("0", rows[0]["AllowOverhang"])
        assertEquals("1", rows[0]["Locked"])

        val beforeSpace = sampleSpace()
        val beforeItems = db.items.size
        val n = NashiIo.importGameSorter(dao, "shelves", rows)

        assertEquals("shelves 分支应回写 1 行", 1, n)
        assertEquals("按空间名命中已有空间, 不得新增空间行", 1, db.spaces.size)
        val after = db.spaces.single()
        assertEquals("必须复用原空间 id 而不是新建", beforeSpace.id, after.id)
        assertEquals("空间必须留在原房间", beforeSpace.roomId, after.roomId)
        assertEquals("空间名还原", "A架", after.name)
        assertEquals("宽还原", 80f, after.width, 0f)
        assertEquals("深还原", 40f, after.depth, 0f)
        assertEquals("层高还原", 30f, after.shelfHeight, 0f)
        assertEquals("列数还原", 2, after.columns)
        assertEquals("最大外悬还原", 5f, after.maxOverhang, 0f)
        assertFalse("allowOverhang 必须还原为 false", after.allowOverhang)
        assertTrue("locked 必须还原为 true", after.locked)
        assertEquals("shelves 行绝不能被当成物品入库", beforeItems, db.items.size)
        assertEquals("物品表仍只有原 1 件", 1, db.items.size)
    }
}
