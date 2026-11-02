package com.nashi.core.optimizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SpaceOptimizer 纯 JVM 测试(零 Android 依赖).
 *
 * 真实行为要点(对照 SpaceOptimizer.kt):
 * - 只使用 `locked == false` 的 shelf,并按 shelf.height 升序尝试 (L14)
 * - 箱子按体积 w*h*d 降序处理 (L15)
 * - 每箱最多 4 种旋转; thisSideUp=true 时只有 2 种 (L18-21, L30)
 * - 深度溢出容忍: `rd <= depth + (allowOverhang ? maxOverhang : 0)` (L33)
 * - 每个 shelf 内按行摆放: 同行并列共享同一基线 y; 当前行剩余宽度放不下则换行(x 归零, y 抬到上—行最高顶面)
 * - `z` 恒为 0f (本轮深度方向不分层); lockInPlace=true 时只允许原始朝向, 不旋转/翻转
 * - 最多 8 轮, deadline 只在轮次之间检查, 所以 timeoutMs 用 60_000 消除墙钟不确定性
 */
class SpaceOptimizerTest {

    private val timeout = 60_000L

    // ------------------------------------------------------------------
    // 不变量: 对任意输入都必须成立
    // ------------------------------------------------------------------

    private fun assertInvariants(boxes: List<Box>, shelves: List<Shelf>, r: SpaceOptimizer.Result) {
        val shelfById = shelves.associateBy { it.id }
        for (p in r.placed) {
            val s = shelfById[p.shelfId]
            assertTrue("placed 落在未知 shelf: ${p.shelfId}", s != null)
            assertFalse("placed 落在锁定 shelf: ${p.shelfId}", s!!.locked)
            val overhang = if (s.allowOverhang) s.maxOverhang else 0f
            assertTrue("rw=${p.rw} 超出 width=${s.width}", p.rw <= s.width)
            assertTrue("rh=${p.rh} 超出 height=${s.height}", p.rh <= s.height)
            assertTrue("rd=${p.rd} 超出 depth+溢出=${s.depth + overhang}", p.rd <= s.depth + overhang)
            assertEquals("z 恒为 0", 0f, p.z, 0f)
        }
        assertEquals("placed 中 box.id 不得重复", r.placed.size, r.placed.map { it.box.id }.toSet().size)
        val placedIds = r.placed.map { it.box.id }.toSet()
        val unshelvedIds = r.unshelved.map { it.id }.toSet()
        assertEquals("placed 与 unshelved 不得重叠", emptySet<String>(), placedIds intersect unshelvedIds)
        assertEquals(
            "placed ∪ unshelved 必须恰好等于输入 boxes",
            boxes.map { it.id }.toSet(),
            placedIds + unshelvedIds,
        )
        assertEquals(
            "shelvesUsed 必须等于实际用到的 shelf 数",
            r.placed.map { it.shelfId }.toSet().size,
            r.shelvesUsed,
        )
    }

    @Test
    fun `不变量在多种输入组合下都成立`() {
        val scenarios: List<Pair<List<Box>, List<Shelf>>> = listOf(
            emptyList<Box>() to emptyList(),
            listOf(Box("solo", 30f, 20f, 10f)) to listOf(Shelf("s1", 80f, 60f, 40f)),
            listOf(
                Box("b1", 30f, 20f, 10f), Box("b2", 25f, 15f, 12f), Box("b3", 12f, 12f, 12f),
                Box("b4", 60f, 30f, 30f), Box("b5", 8f, 8f, 8f), Box("b6", 45f, 10f, 10f),
            ) to listOf(
                Shelf("s1", 80f, 60f, 40f, allowOverhang = true, maxOverhang = 10f),
                Shelf("s2", 50f, 50f, 50f, allowOverhang = false),
                Shelf("s3", 40f, 40f, 40f, locked = true),
                Shelf("s4", 20f, 20f, 20f, allowOverhang = true, maxOverhang = 0f),
            ),
            listOf(
                Box("t1", 10f, 10f, 40f, thisSideUp = true),
                Box("t2", 40f, 10f, 10f, thisSideUp = true),
            ) to listOf(Shelf("s1", 50f, 50f, 50f, allowOverhang = false)),
            listOf(Box("x1", 5f, 5f, 5f), Box("x2", 5f, 5f, 5f), Box("x3", 5f, 5f, 5f)) to
                listOf(Shelf("s1", 12f, 12f, 12f, allowOverhang = false), Shelf("s2", 30f, 30f, 30f)),
            listOf(Box("y1", 100f, 100f, 100f), Box("y2", 1f, 1f, 1f)) to
                listOf(Shelf("s1", 1f, 1f, 1f, allowOverhang = true, maxOverhang = 0f)),
        )
        for ((boxes, shelves) in scenarios) {
            val r = SpaceOptimizer.autoSort(boxes, shelves, timeout)
            assertInvariants(boxes, shelves, r)
        }
    }

    // ------------------------------------------------------------------
    // 边界场景
    // ------------------------------------------------------------------

    @Test
    fun `空输入返回空结果`() {
        val r = SpaceOptimizer.autoSort(emptyList(), emptyList(), timeout)
        assertTrue(r.placed.isEmpty())
        assertTrue(r.unshelved.isEmpty())
        assertEquals(0, r.shelvesUsed)
    }

    @Test
    fun `单箱单架可放下`() {
        val box = Box("b1", 10f, 5f, 4f)
        val shelf = Shelf("s1", 100f, 50f, 40f, allowOverhang = false)
        val r = SpaceOptimizer.autoSort(listOf(box), listOf(shelf), timeout)

        assertEquals(1, r.placed.size)
        assertTrue(r.unshelved.isEmpty())
        assertEquals(1, r.shelvesUsed)

        val p = r.placed[0]
        assertEquals("b1", p.box.id)
        assertEquals("s1", p.shelfId)
        assertEquals(0f, p.x, 0f)
        assertEquals(0f, p.y, 0f)
        assertEquals("z 恒为 0", 0f, p.z, 0f)
        assertTrue("rw 必须 > 0", p.rw > 0f)
        assertTrue("rh 必须 > 0", p.rh > 0f)
        assertTrue("rd 必须 > 0", p.rd > 0f)
        // 第 0 轮先试原始朝向 (w,h,d)
        assertEquals(10f, p.rw, 0f)
        assertEquals(5f, p.rh, 0f)
        assertEquals(4f, p.rd, 0f)
    }

    @Test
    fun `箱子过大且不允许前伸时进入 unshelved`() {
        val box = Box("big", 20f, 20f, 20f)
        val shelf = Shelf("s1", 10f, 10f, 10f, allowOverhang = false)
        val r = SpaceOptimizer.autoSort(listOf(box), listOf(shelf), timeout)

        assertTrue(r.placed.isEmpty())
        assertEquals(listOf("big"), r.unshelved.map { it.id })
        assertEquals(0, r.shelvesUsed)
    }

    @Test
    fun `深度溢出在 maxOverhang 内放行 超出则拒绝`() {
        // 只有 depth 方向超标: 18 需要超出 depth=10
        val deep = Box("deep", 8f, 8f, 18f)
        val tight = Shelf("tight", 10f, 10f, 10f, allowOverhang = true, maxOverhang = 5f)
        val loose = Shelf("loose", 10f, 10f, 10f, allowOverhang = true, maxOverhang = 10f)
        val noOverhang = Shelf("none", 10f, 10f, 10f, allowOverhang = false, maxOverhang = 100f)

        val rTight = SpaceOptimizer.autoSort(listOf(deep), listOf(tight), timeout)
        assertTrue("rd=18 > depth+maxOverhang=15 必须被排除", rTight.placed.isEmpty())
        assertEquals(listOf("deep"), rTight.unshelved.map { it.id })

        val rNone = SpaceOptimizer.autoSort(listOf(deep), listOf(noOverhang), timeout)
        assertTrue("allowOverhang=false 时不许前伸", rNone.placed.isEmpty())

        val rLoose = SpaceOptimizer.autoSort(listOf(deep), listOf(loose), timeout)
        assertEquals(1, rLoose.placed.size)
        val p = rLoose.placed[0]
        assertEquals(18f, p.rd, 0f)
        assertTrue("确实用到了前伸", p.rd > loose.depth)
        assertTrue(p.rd <= loose.depth + loose.maxOverhang)
        assertInvariants(listOf(deep), listOf(loose), rLoose)
    }

    @Test
    fun `锁定 shelf 被排除`() {
        val box = Box("b1", 5f, 5f, 5f)
        val r = SpaceOptimizer.autoSort(
            listOf(box),
            listOf(Shelf("locked", 500f, 500f, 500f, locked = true)),
            timeout,
        )
        assertTrue(r.placed.isEmpty())
        assertEquals(listOf("b1"), r.unshelved.map { it.id })
        assertEquals(0, r.shelvesUsed)
    }

    @Test
    fun `同时有锁定与未锁定 shelf 时只落到未锁定那个`() {
        val box = Box("b1", 5f, 5f, 5f)
        val shelves = listOf(
            Shelf("locked", 500f, 500f, 500f, locked = true),
            Shelf("free", 50f, 50f, 50f),
        )
        val r = SpaceOptimizer.autoSort(listOf(box), shelves, timeout)
        assertEquals(1, r.placed.size)
        assertEquals("free", r.placed[0].shelfId)
        assertEquals(1, r.shelvesUsed)
        assertInvariants(listOf(box), shelves, r)
    }

    // ------------------------------------------------------------------
    // 算法特征(记录真实行为)
    // ------------------------------------------------------------------

    @Test
    fun `箱子按体积降序处理`() {
        val big = Box("big", 10f, 10f, 10f)    // 体积 1000
        val small = Box("small", 6f, 2f, 2f)   // 体积 24
        val shelf = Shelf("s1", 10f, 10f, 10f, allowOverhang = false)

        // 若先放 small, big(宽 10) 就再也放不下; 真实行为是 big 先放, small 无处可放
        val r = SpaceOptimizer.autoSort(listOf(small, big), listOf(shelf), timeout)
        assertEquals(listOf("big"), r.placed.map { it.box.id })
        assertEquals(listOf("small"), r.unshelved.map { it.id })
    }

    /**
     * 同行并列的两箱必须共享同一基线 y — 回归旧 bug:
     * 旧实现只在 x == 0f 时更新 stackTop, 导致同行的第二箱被错误抬到第一箱顶面。
     */
    @Test
    fun `同行并列的两箱共享同一基线`() {
        val shelves = listOf(Shelf("s1", 100f, 50f, 20f, allowOverhang = false))
        val boxes = listOf(Box("b1", 30f, 10f, 5f), Box("b2", 20f, 10f, 5f))
        val r = SpaceOptimizer.autoSort(boxes, shelves, timeout)

        assertEquals(2, r.placed.size)
        val p1 = r.placed.first { it.box.id == "b1" }
        val p2 = r.placed.first { it.box.id == "b2" }
        val shelf = shelves.single()
        // 第一个箱子从原点起摆
        assertEquals(0f, p1.x, 0f)
        assertEquals(0f, p1.y, 0f)
        // 第二箱紧贴第一箱右侧(只断言相对关系, 不锁定绝对坐标数值)
        assertEquals("第二箱应紧贴第一箱右侧", p1.rw, p2.x, 1e-4f)
        // 关键: 同一行内基线必须相同, 不能被抬到第一箱顶面
        assertEquals("同行并列的第二箱基线必须与第一箱一致", p1.y, p2.y, 1e-4f)
        // 两箱都必须落在货架边界内且互不重叠
        assertTrue("超出货架宽度", p1.x + p1.rw <= shelf.width && p2.x + p2.rw <= shelf.width)
        assertTrue("超出货架高度", p1.y + p1.rh <= shelf.height && p2.y + p2.rh <= shelf.height)
        assertTrue(
            "两箱投影重叠",
            p2.x >= p1.x + p1.rw || p1.x >= p2.x + p2.rw || p2.y >= p1.y + p1.rh || p1.y >= p2.y + p2.rh,
        )
        assertInvariants(boxes, shelves, r)
    }

    /**
     * 当前行放不下时才换行: 新行基线 = 上一行已用最高顶面, 垂直堆叠不能因此丢失。
     */
    @Test
    fun `当前行放不下时换行并抬到上一行最高顶面`() {
        // 货架宽 40, 箱子宽 30: 同一行只装得下 1 个, 第二个必须换行堆叠
        val shelves = listOf(Shelf("s1", 40f, 50f, 20f, allowOverhang = false))
        val boxes = listOf(Box("b1", 30f, 10f, 5f), Box("b2", 30f, 10f, 5f))
        val r = SpaceOptimizer.autoSort(boxes, shelves, timeout)

        assertEquals(2, r.placed.size)
        val p1 = r.placed.first { it.box.id == "b1" }
        val p2 = r.placed.first { it.box.id == "b2" }
        assertEquals(0f, p1.y, 0f)
        assertEquals("换行后的箱子回到行首", 0f, p2.x, 0f)
        assertEquals("新行基线应等于上一行最高顶面", p1.y + p1.rh, p2.y, 1e-4f)
        assertTrue("垂直堆叠后不得超出货架高度", p2.y + p2.rh <= shelves.single().height)
        assertInvariants(boxes, shelves, r)
    }

    @Test
    fun `同一输入连续两次运行结果完全一致`() {
        val boxes = listOf(
            Box("b1", 30f, 20f, 10f), Box("b2", 25f, 15f, 12f), Box("b3", 12f, 12f, 12f),
            Box("b4", 60f, 30f, 30f), Box("b5", 8f, 8f, 8f),
        )
        val shelves = listOf(
            Shelf("s1", 80f, 60f, 40f),
            Shelf("s2", 50f, 50f, 50f, allowOverhang = false),
            Shelf("s3", 40f, 40f, 40f, locked = true),
        )

        fun fingerprint() = SpaceOptimizer.autoSort(boxes, shelves, timeout).placed
            .map { "${it.box.id}|${it.shelfId}|${it.x}|${it.y}|${it.z}" }

        val first = fingerprint()
        val second = fingerprint()
        assertTrue("确定性用例应当确实摆下了东西", first.isNotEmpty())
        assertEquals(first, second)
    }

    /**
     * lockInPlace 语义(本轮落码的定论): 锁定“朝向” —— 不旋转/不翻转, 只用原始 (w,h,d) 试放;
     * 坐标仍由算法决定(Box 不携带坐标, 无从锁定位置), 且优先级高于 thisSideUp。
     */
    @Test
    fun `lockInPlace 箱子保持原朝向且不改尺寸`() {
        val fixed = Box("fixed", 5f, 6f, 7f, lockInPlace = true)
        val r = SpaceOptimizer.autoSort(listOf(fixed), listOf(Shelf("s1", 50f, 50f, 50f)), timeout)

        assertEquals(1, r.placed.size)
        val p = r.placed[0]
        assertEquals("fixed", p.box.id)
        assertEquals("锁向箱必须用原始宽", 5f, p.rw, 0f)
        assertEquals("锁向箱必须用原始高", 6f, p.rh, 0f)
        assertEquals("锁向箱必须用原始深", 7f, p.rd, 0f)
    }

    @Test
    fun `lockInPlace 箱子不旋转 只能靠旋转放下的箱子会进 unshelved`() {
        // 原始朝向 (10,4,26) 在 depth=10 的货架上放不下, 只有旋转 (10,26,4) 才放得下
        val shelves = listOf(Shelf("s1", 10f, 30f, 10f, allowOverhang = false))
        val locked = Box("rot", 10f, 4f, 26f, lockInPlace = true)
        val free = Box("rot", 10f, 4f, 26f)

        val rLocked = SpaceOptimizer.autoSort(listOf(locked), shelves, timeout)
        assertTrue("锁向箱不允许旋转, 必须进 unshelved", rLocked.placed.isEmpty())
        assertEquals(listOf("rot"), rLocked.unshelved.map { it.id })

        val rFree = SpaceOptimizer.autoSort(listOf(free), shelves, timeout)
        assertEquals("未锁向的同尺寸箱子应能靠旋转放下", 1, rFree.placed.size)
        assertEquals(4f, rFree.placed[0].rd, 0f)
    }

    @Test
    fun `lockInPlace 优先于 thisSideUp`() {
        // thisSideUp 允许 2 种朝向; 叠加 lockInPlace 后只允许原始朝向 -> 放不下
        val b = Box("both", 10f, 4f, 26f, thisSideUp = true, lockInPlace = true)
        val shelves = listOf(Shelf("s1", 10f, 30f, 10f, allowOverhang = false))
        val r = SpaceOptimizer.autoSort(listOf(b), shelves, timeout)

        assertTrue("lockInPlace 应压过 thisSideUp 的两种朝向", r.placed.isEmpty())
        assertEquals(listOf("both"), r.unshelved.map { it.id })
    }

    // ------------------------------------------------------------------
    // 任务书点名的必测场景
    // ------------------------------------------------------------------

    @Test
    fun `全部装得下时 unshelved 为空且计数守恒`() {
        val boxes = listOf(Box("b1", 30f, 10f, 5f), Box("b2", 20f, 10f, 5f), Box("b3", 10f, 10f, 5f))
        val shelves = listOf(Shelf("s1", 100f, 50f, 20f, allowOverhang = false))
        val r = SpaceOptimizer.autoSort(boxes, shelves, timeout)

        assertEquals("计数守恒: placed + unshelved == boxes", boxes.size, r.placed.size + r.unshelved.size)
        assertTrue("至少要装下一个", r.placed.size >= 1)
        assertEquals("本用例构造上应全部装下", 3, r.placed.size)
        assertTrue("全部装下时 unshelved 必须为空", r.unshelved.isEmpty())
        assertInvariants(boxes, shelves, r)
    }

    @Test
    fun `空间不足时部分箱子进入 unshelved 其余仍被装入`() {
        // 货架 40x20 只能容纳 2 个 20x20 的立方箱(每行 2 个, 无纵向余量);
        // 用立方箱(四种旋转结果相同)保证没有“靠旋转变小”的逃逸路径, 容量上限是真实的
        val boxes = listOf(Box("b1", 20f, 20f, 20f), Box("b2", 20f, 20f, 20f), Box("b3", 20f, 20f, 20f))
        val shelves = listOf(Shelf("s1", 40f, 20f, 20f, allowOverhang = false))
        val r = SpaceOptimizer.autoSort(boxes, shelves, timeout)

        assertEquals("计数守恒: placed + unshelved == boxes", boxes.size, r.placed.size + r.unshelved.size)
        assertTrue("至少有 1 个装箱失败", r.unshelved.size >= 1)
        assertTrue("至少有 1 个装箱成功", r.placed.size >= 1)
        assertEquals("构造上恰好剩 1 个装不下", 1, r.unshelved.size)
        assertEquals(2, r.placed.size)
        assertInvariants(boxes, shelves, r)
    }

    @Test
    fun `超时熔断 timeoutMs 为 0 时快速返回且结果结构合法`() {
        // 大箱子列表 + 单个超大货架: 若熔断失效会做满 8 轮 * 每箱多次旋转
        val boxes = (1..2000).map { Box("b$it", 10f, 10f, 10f) }
        val shelves = listOf(Shelf("s1", 1_000_000f, 1_000_000f, 1_000_000f, allowOverhang = false))

        val startNs = System.nanoTime()
        val r = SpaceOptimizer.autoSort(boxes, shelves, timeoutMs = 0)
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000

        assertTrue("timeoutMs=0 必须立刻返回, 实测 ${elapsedMs}ms", elapsedMs < 10_000)
        // deadline 只在轮次之间检查, 且毫秒精度可能同刻, 因此不锁定 placed 数量, 只校验结构合法
        assertInvariants(boxes, shelves, r)
        assertEquals(boxes.size, r.placed.size + r.unshelved.size)
        assertTrue(r.placed.size in 0..boxes.size)
    }

    @Test
    fun `只有旋转后才装得下的箱子确实被装入`() {
        // 原始朝向 (w,h,d)=(10,4,26): 深度 26 > 货架 depth 10, 且不允许前伸 -> 必须靠旋转
        // 唯一可行的旋转是 (w,d,h)=(10,26,4): 宽 10<=10, 高 26<=30, 深 4<=10
        val box = Box("rot", 10f, 4f, 26f)
        val shelves = listOf(Shelf("s1", 10f, 30f, 10f, allowOverhang = false))
        val r = SpaceOptimizer.autoSort(listOf(box), shelves, timeout)

        assertEquals("旋转后应当装下", 1, r.placed.size)
        assertTrue(r.unshelved.isEmpty())
        val p = r.placed[0]
        assertEquals("rw", 10f, p.rw, 0f)
        assertEquals("rh", 26f, p.rh, 0f)
        assertEquals("rd", 4f, p.rd, 0f)
        assertTrue("确实换了朝向: 原始深度 26 放不进 depth=10", p.rd <= 10f)
        assertInvariants(listOf(box), shelves, r)
    }
}
