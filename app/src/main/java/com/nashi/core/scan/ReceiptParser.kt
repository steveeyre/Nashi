package com.nashi.core.scan

/** 小票识别后的一行商品: 名称、数量、单位、金额(可能缺失)。 */
data class ReceiptLine(
    val name: String,
    val quantity: Double = 1.0,
    val unit: String = "件",
    val amount: Double? = null,
)

/**
 * 小票 OCR 文本 -> 商品行。纯 Kotlin, 可纯 JVM 测。
 *
 * 规则(按行独立处理):
 * 1. 跳过空白行与表头/合计/支付/店铺信息等关键词行;
 * 2. 去掉行首序号(如 "1." "2、");
 * 3. 行尾连续数字视为金额区, 最后一个数字是金额; 若金额区有 >=3 个数, 第一个是数量;
 * 4. 名称里的 "x2" / "2瓶" 覆盖数量与单位;
 * 5. 既无金额也无数量标记的行(多为店名/地址)丢弃。
 */
object ReceiptParser {
    private const val UNIT_PATTERN = "瓶|袋|盒|包|罐|支|条|个|件|提|箱|斤|kg|g|ml|L"
    private val SKIP_WORDS = listOf(
        "小票", "合计", "总计", "应付", "实付", "收款", "找零", "优惠", "折扣", "会员", "积分",
        "发票", "税额", "收银", "电话", "地址", "日期", "时间", "单号", "流水", "欢迎", "谢谢",
        "客服", "微信", "支付宝", "现金", "银行卡", "商品名称", "数量", "单价", "金额",
    )
    private val SEQ_PREFIX = Regex("""^\d{1,3}[.、)）]\s*""")
    private val TRAILING_NUMBERS = Regex("""(?:\s*[¥￥]?\s*\d+(?:[.,]\d{1,2})?\s*元?\s*)+$""")
    private val NUMBER = Regex("""\d+(?:[.,]\d{1,2})?""")
    private val QTY_CROSS = Regex("""[xX×*]\s*(\d+(?:\.\d+)?)""")
    private val QTY_SUFFIX = Regex("""(\d+(?:\.\d+)?)\s*($UNIT_PATTERN)\s*$""", RegexOption.IGNORE_CASE)

    fun parse(raw: String): List<ReceiptLine> =
        raw.lineSequence().mapNotNull { parseLine(it) }.toList()

    fun parseLine(raw: String): ReceiptLine? {
        var line = raw.trim().trim('*', '-', '—', '·', '|')
        if (line.isEmpty()) return null
        val compact = line.replace(" ", "")
        if (SKIP_WORDS.any { compact.contains(it, ignoreCase = true) }) return null
        line = line.replace(SEQ_PREFIX, "").trim()
        if (line.isEmpty()) return null

        // 先摘掉 "x3" 再找行尾数字区: 否则正则会把数量里的 3 当成金额区的一部分
        var crossQty: Double? = null
        QTY_CROSS.find(line)?.let {
            crossQty = it.groupValues[1].toDoubleOrNull()
            line = line.removeRange(it.range).trim()
        }

        val tail = TRAILING_NUMBERS.find(line)
        var rest = if (tail != null) line.substring(0, tail.range.first) else line
        val numbers = tail?.let {
            NUMBER.findAll(it.value).mapNotNull { m -> m.value.replace(",", ".").toDoubleOrNull() }.toList()
        } ?: emptyList()

        if (tail == null && crossQty == null && !QTY_SUFFIX.containsMatchIn(rest)) {
            return null
        }

        var quantity = crossQty ?: if (numbers.size >= 3) numbers.first() else 1.0
        var unit = "件"

        QTY_SUFFIX.find(rest)?.let {
            val q = it.groupValues[1].toDoubleOrNull() ?: return@let
            // 数量单位只接受小数量; 128G/500ml 这类规格留在名称里
            if (q > 99) return@let
            quantity = q
            unit = it.groupValues[2]
            rest = rest.removeRange(it.range)
        }

        val name = rest.trim().trim('*', '-', '—', '·', '|', ' ', '.', ':', '：')
        if (name.isBlank()) return null
        return ReceiptLine(name, quantity, unit, numbers.lastOrNull())
    }
}
