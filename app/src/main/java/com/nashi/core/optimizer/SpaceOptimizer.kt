package com.nashi.core.optimizer

// GameSorter 算法 Kotlin 移植: 长方体装箱, 2D/3D 同布局.
// 原逻辑: shelf 内按宽x高+深堆叠, 允许旋转, 评分=装下最多>用 shelf 最少>前挡最少>碎片最少>前伸最少, 12s 超时.
data class Box(val id: String, var w: Float, var h: Float, var d: Float, val thisSideUp: Boolean = false, val lockInPlace: Boolean = false)
data class Placed(val box: Box, val shelfId: String, val x: Float, val y: Float, val z: Float, val rw: Float, val rh: Float, val rd: Float)
data class Shelf(val id: String, val width: Float, val height: Float, val depth: Float, val allowOverhang: Boolean = true, val maxOverhang: Float = 10f, val locked: Boolean = false)

object SpaceOptimizer {
    data class Result(val placed: List<Placed>, val unshelved: List<Box>, val shelvesUsed: Int)

    /**
     * 排布骨架: 每个 shelf 内部按“行”摆放 —— 行内自左向右累加 x, 当前行剩余宽度放不下则换行,
     * 新行基线 y = 上一行的最高顶面. 每个箱子的基线严格按它所在的行取, 因此同行并列的箱子共享同一基线.
     * 深度方向本轮不分层(z 恒 0), 只按 depth(+前伸容忍)做准入判断.
     *
     * lockInPlace 语义: 锁定朝向 —— 不旋转/不翻转, 只允许原始 (w, h, d) 参与试放;
     * 它约束的是“朝向”而不是“坐标”(Box 本身不携带坐标, 位置仍由算法决定), 且优先级高于 thisSideUp。
     */
    fun autoSort(boxes: List<Box>, shelves: List<Shelf>, timeoutMs: Long = 12_000): Result {
        val deadline = System.currentTimeMillis() + timeoutMs
        val open = shelves.filter { !it.locked }.sortedBy { it.height }
        val sortedBoxes = boxes.sortedByDescending { it.w * it.h * it.d }
        var best: List<Placed> = emptyList()
        // 贪心多轮: 按体积从大到小试放, 每轮换旋转顺序, 取装下最多的布局
        val rotations: List<(Box) -> Triple<Float, Float, Float>> = listOf(
            { b -> Triple(b.w, b.h, b.d) }, { b -> Triple(b.h, b.w, b.d) },
            { b -> Triple(b.d, b.h, b.w) }, { b -> Triple(b.w, b.d, b.h) },
        )
        for (round in 0 until 8) {
            if (System.currentTimeMillis() > deadline) break
            val placed = mutableListOf<Placed>()
            val cursor = mutableMapOf<String, Float>() // shelfId -> 当前行内下一个空闲 x
            val rowBase = mutableMapOf<String, Float>() // shelfId -> 当前行基线 y
            val rowTop = mutableMapOf<String, Float>() // shelfId -> 当前行最高顶面 y
            for (b in sortedBoxes) {
                var done = false
                for (s in open) {
                    val dims = when {
                        // lockInPlace: 锁定朝向, 只允许原始 (w, h, d), 不参与任何旋转/翻转
                        b.lockInPlace -> listOf(Triple(b.w, b.h, b.d))
                        b.thisSideUp -> listOf(Triple(b.w, b.h, b.d), Triple(b.d, b.h, b.w))
                        else -> rotations.map { it(b) }.let { listOf(it[round % it.size]) + it }
                    }
                    for ((rw, rh, rd) in dims) {
                        if (rw > s.width || rh > s.height || rd > s.depth + (if (s.allowOverhang) s.maxOverhang else 0f)) continue
                        // 定位: 先试当前行游标处; 当前行剩余宽度放不下则换行(游标归零, 基线抬到上一行最高顶面)
                        var x = cursor.getOrDefault(s.id, 0f)
                        var y = rowBase.getOrDefault(s.id, 0f)
                        val wrapped = x + rw > s.width
                        if (wrapped) {
                            x = 0f
                            y = rowTop.getOrDefault(s.id, y)
                        }
                        if (y + rh > s.height) continue
                        placed.add(Placed(b, s.id, x, y, 0f, rw, rh, rd))
                        cursor[s.id] = x + rw
                        rowBase[s.id] = y
                        // 换行后本行高度从该箱重新累计; 否则取本行已用高度的最大值
                        rowTop[s.id] = if (wrapped) y + rh else maxOf(rowTop.getOrDefault(s.id, y), y + rh)
                        done = true; break
                    }
                    if (done) break
                }
            }
            // 评分: 装下最多, 用 shelf 最少
            if (placed.size > best.size || (placed.size == best.size && placed.map { it.shelfId }.toSet().size < best.map { it.shelfId }.toSet().size)) {
                best = placed.toList()
            }
        }
        val placedIds = best.map { it.box.id }.toSet()
        return Result(best, sortedBoxes.filter { it.id !in placedIds }, best.map { it.shelfId }.toSet().size)
    }
}
