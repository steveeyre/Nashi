package com.nashi.feature.stats

import org.junit.Assert.assertEquals
import org.junit.Test

class AxisTicksTest {

    @Test
    fun `最大值为 1 时只保留 0 和 1`() {
        val raw = (0..9).map { it / 9f }
        assertEquals(listOf(0f, 1f), AxisTicks.snap(raw, 1f))
    }

    @Test
    fun `小值域步长不小于 1`() {
        assertEquals(1f, AxisTicks.step(4f))
        assertEquals(1f, AxisTicks.step(1f))
    }

    @Test
    fun `较大值域按整数步长取刻度`() {
        val raw = (0..9).map { it * 30f / 9f }
        assertEquals(listOf(0f, 8f, 16f, 24f), AxisTicks.snap(raw, 30f))
    }

    @Test
    fun `刻度永不出现小数`() {
        listOf(7f, 13f, 99f).forEach { max ->
            val raw = (0..9).map { it * max / 9f }
            AxisTicks.snap(raw, max).forEach { v ->
                assertEquals(v.toString(), v.toInt().toFloat(), v)
            }
        }
    }

    @Test
    fun `空输入返回空`() {
        assertEquals(emptyList<Float>(), AxisTicks.snap(emptyList(), 10f))
    }
}
