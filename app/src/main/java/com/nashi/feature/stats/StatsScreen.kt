package com.nashi.feature.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nashi.core.data.StatRow
import com.nashi.core.model.Item
import com.nashi.ui.NashiEmptyState
import com.nashi.ui.NashiStatTile
import com.nashi.ui.theme.accentAt
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.chart.draw.ChartDrawContext
import com.patrykandpatrick.vico.core.chart.values.ChartValues
import com.patrykandpatrick.vico.core.entry.FloatEntry
import com.patrykandpatrick.vico.core.entry.entryModelOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 基础功能9: 分类统计(分房间/类别/品牌 + 标签占位).
 * 顶部 Tab(房间|空间|类别|品牌|标签), 每页 Vico 柱状图(件数 c)+ 明细列表(k 名称、c 件数、q 总量).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: StatsViewModel = hiltViewModel()) {
    val byRoom by vm.byRoom.collectAsState()
    val bySpace by vm.bySpace.collectAsState()
    val byCategory by vm.byCategory.collectAsState()
    val byBrand by vm.byBrand.collectAsState()
    val byTag by vm.byTag.collectAsState()
    val roomNames by vm.roomNames.collectAsState()
    val spaceNames by vm.spaceNames.collectAsState()
    val categoryNames by vm.categoryNames.collectAsState()
    val expiringCount by vm.expiringCount.collectAsState()
    val lowStockCount by vm.lowStockCount.collectAsState()
    val expiringItems by vm.expiringItems.collectAsState()
    val lowStockItems by vm.lowStockItems.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var showReminders by remember { mutableStateOf(false) }
    val totalCount = byRoom.sumOf { it.c }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("统计", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = { IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, "刷新") } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NashiStatTile(
                    value = totalCount.toString(),
                    label = "在库件数",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                NashiStatTile(
                    value = expiringCount.toString(),
                    label = "近30天到期",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
                NashiStatTile(
                    value = lowStockCount.toString(),
                    label = "低库存",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
            }
            if (expiringCount > 0 || lowStockCount > 0) {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable { showReminders = true },
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "近30天过期 $expiringCount 件 · 低库存 $lowStockCount 件",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            "查看明细",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            TabRow(
                selectedTabIndex = tab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)) },
            ) {
                listOf("房间", "空间", "类别", "品牌", "标签").forEachIndexed { i, title ->
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i },
                        text = {
                            Text(
                                title,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                            )
                        },
                    )
                }
            }
            when (tab) {
                0 -> StatPage(
                    rows = byRoom,
                    emptyHint = "添加物品并指定房间后，这里会自动汇总。",
                    nameOf = { k -> roomNames[k] ?: if (k.isBlank()) "未分配" else k.take(8) },
                )
                1 -> StatPage(
                    rows = bySpace,
                    emptyHint = "给物品指定货架或收纳箱后，这里会按空间汇总。",
                    nameOf = { k -> spaceNames[k] ?: if (k.isBlank()) "未分配" else k.take(8) },
                )
                2 -> StatPage(
                    rows = byCategory,
                    emptyHint = "给物品选择分类后，这里会按类别汇总。",
                    nameOf = { k -> categoryNames[k] ?: if (k.isBlank()) "未分类" else k.take(8) },
                )
                3 -> StatPage(
                    rows = byBrand,
                    emptyHint = "在物品里填写品牌后，这里会按品牌汇总。",
                    nameOf = { k -> k.ifBlank { "未填品牌" } },
                )
                else -> StatPage(
                    rows = byTag,
                    emptyHint = "给物品打上标签后，这里会按标签汇总。",
                    nameOf = { k -> k.ifBlank { "未命名标签" } },
                )
            }
        }
    }
    if (showReminders) {
        ReminderDialog(
            expiring = expiringItems,
            lowStock = lowStockItems,
            onDismiss = { showReminders = false },
        )
    }
}

@Composable
private fun ReminderDialog(expiring: List<Item>, lowStock: List<Item>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("提醒明细") },
        text = {
            Column(
                Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("近30天过期/已过期 (${expiring.size})", style = MaterialTheme.typography.titleSmall)
                if (expiring.isEmpty()) {
                    Text("暂无", style = MaterialTheme.typography.bodySmall)
                } else {
                    expiring.forEach { Text("${it.name} · ${dateText(it.expiryDate)}", style = MaterialTheme.typography.bodySmall) }
                }
                Spacer(Modifier.height(8.dp))
                Text("低于最低库存 (${lowStock.size})", style = MaterialTheme.typography.titleSmall)
                if (lowStock.isEmpty()) {
                    Text("暂无", style = MaterialTheme.typography.bodySmall)
                } else {
                    lowStock.forEach {
                        Text(
                            "${it.name} · ${fmtQty(it.quantity)}/${fmtQty(it.minStock)} ${it.unit}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

private fun dateText(ts: Long?): String =
    ts?.let { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it)) } ?: "-"

@Composable
private fun StatPage(rows: List<StatRow>, emptyHint: String, nameOf: (String) -> String) {
    val sorted = remember(rows) { rows.sortedByDescending { it.c } }
    val top = remember(sorted) { sorted.take(12) }
    if (sorted.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            NashiEmptyState(
                icon = Icons.Default.BarChart,
                title = "暂无统计数据",
                message = emptyHint,
            )
        }
        return
    }
    val maxCount = remember(sorted) { sorted.maxOf { it.c }.coerceAtLeast(1) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ElevatedCard(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(
                        "件数分布 · 前 " + top.size + " 项",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    val labels = top.map { nameOf(it.k) }
                    val model = remember(top) {
                        entryModelOf(top.mapIndexed { i, r -> FloatEntry(i.toFloat(), r.c.toFloat()) })
                    }
                    Chart(
                        chart = columnChart(),
                        model = model,
                        startAxis = rememberStartAxis(
                            valueFormatter = intAxisFormatter(),
                            itemPlacer = intLabelPlacer(top.maxOf { it.c }.toFloat()),
                        ),
                        bottomAxis = rememberBottomAxis(
                            valueFormatter = bottomLabels(labels),
                        ),
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                    )
                }
            }
        }
        itemsIndexed(sorted, key = { _, r -> r.k.ifBlank { "__blank__" } }) { index, r ->
            val accent = accentAt(index)
            ElevatedCard(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(shape = CircleShape, color = accent.container) {
                        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                            Text(
                                (index + 1).toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = accent.content,
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                nameOf(r.k),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                r.c.toString() + " 件",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { r.c.toFloat() / maxCount },
                            modifier = Modifier.fillMaxWidth().height(5.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primaryContainer,
                            strokeCap = StrokeCap.Round,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "总量 " + fmtQty(r.q),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun bottomLabels(labels: List<String>): AxisValueFormatter<AxisPosition.Horizontal.Bottom> =
    object : AxisValueFormatter<AxisPosition.Horizontal.Bottom> {
        override fun formatValue(value: Float, chartValues: ChartValues): CharSequence =
            labels.getOrNull(value.toInt())?.take(4) ?: ""
    }

// 件数只可能是整数, 纵轴标签一律取整, 避免出现 0.11/0.22 这类刻度。
private fun intAxisFormatter(): AxisValueFormatter<AxisPosition.Vertical.Start> =
    object : AxisValueFormatter<AxisPosition.Vertical.Start> {
        override fun formatValue(value: Float, chartValues: ChartValues): CharSequence =
            value.roundToInt().toString()
    }

// 默认刻度按区间等分(小值域会落在小数上), 这里吸附到整数步长。
private fun intLabelPlacer(maxValue: Float): AxisItemPlacer.Vertical {
    val delegate = AxisItemPlacer.Vertical.default()

    return object : AxisItemPlacer.Vertical by delegate {
        override fun getLabelValues(
            context: ChartDrawContext,
            minY: Float,
            maxY: Float,
            position: AxisPosition.Vertical,
        ): List<Float> = AxisTicks.snap(delegate.getLabelValues(context, minY, maxY, position), maxValue)

        override fun getLineValues(
            context: ChartDrawContext,
            minY: Float,
            maxY: Float,
            position: AxisPosition.Vertical,
        ): List<Float> =
            AxisTicks.snap(delegate.getLineValues(context, minY, maxY, position) ?: emptyList(), maxValue)
    }
}

private fun fmtQty(q: Double): String =
    if (q % 1.0 == 0.0) q.toLong().toString() else String.format("%.1f", q)
