package com.nashi.feature.stats

import kotlin.math.ceil
import kotlin.math.roundToInt

/** 件数轴刻度: 步长至少为 1, 标签吸附到步长的整数倍。 */
object AxisTicks {
    fun step(maxValue: Float): Float = maxOf(1, ceil(maxValue / 4f).toInt()).toFloat()

    fun snap(values: List<Float>, maxValue: Float): List<Float> {
        val s = step(maxValue)
        return values.map { (it / s).roundToInt() * s }
            .filter { it >= 0f && it <= maxValue }
            .distinct()
            .sorted()
    }
}
