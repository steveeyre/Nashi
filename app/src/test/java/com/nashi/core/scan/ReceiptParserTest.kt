package com.nashi.core.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptParserTest {

    @Test
    fun `数量 单价 金额 三列时取首个数字为数量`() {
        val line = ReceiptParser.parseLine("1. 牛奶 2 6.25 12.50")
        assertEquals("牛奶", line?.name)
        assertEquals(2.0, line?.quantity ?: 0.0, 0.001)
        assertEquals(12.5, line?.amount ?: 0.0, 0.001)
    }

    @Test
    fun `名称后数量单位与金额`() {
        val line = ReceiptParser.parseLine("可乐 2瓶 8.00")
        assertEquals("可乐", line?.name)
        assertEquals(2.0, line?.quantity ?: 0.0, 0.001)
        assertEquals("瓶", line?.unit)
        assertEquals(8.0, line?.amount ?: 0.0, 0.001)
    }

    @Test
    fun `x 乘号写法识别数量`() {
        val line = ReceiptParser.parseLine("薯片 x3 15.9")
        assertEquals("薯片", line?.name)
        assertEquals(3.0, line?.quantity ?: 0.0, 0.001)
        assertEquals(15.9, line?.amount ?: 0.0, 0.001)
    }

    @Test
    fun `合计与店铺信息行被丢弃`() {
        assertNull(ReceiptParser.parseLine("合 计 25.60"))
        assertNull(ReceiptParser.parseLine("小 票"))
        assertNull(ReceiptParser.parseLine("欢迎光临"))
        assertNull(ReceiptParser.parseLine("地址: 幸福路 1 号"))
    }

    @Test
    fun `容量规格不会被当成数量`() {
        val line = ReceiptParser.parseLine("农夫山泉 550ml 2.00")
        assertEquals("农夫山泉 550ml", line?.name)
        assertEquals(1.0, line?.quantity ?: 0.0, 0.001)
        assertEquals("件", line?.unit)
    }

    @Test
    fun `按行解析跳过空行`() {
        val raw = """
            小票号 0001

            1. 苹果 1斤 5.50
            2. 面包 2 4.00 8.00
            合计 13.50
        """.trimIndent()
        val lines = ReceiptParser.parse(raw)
        assertEquals(2, lines.size)
        assertEquals("苹果", lines[0].name)
        assertEquals("斤", lines[0].unit)
        assertEquals("面包", lines[1].name)
        assertEquals(2.0, lines[1].quantity, 0.001)
        assertTrue(lines.all { it.name.isNotBlank() })
    }
}
