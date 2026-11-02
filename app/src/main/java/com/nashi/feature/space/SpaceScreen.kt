package com.nashi.feature.space

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nashi.core.model.Item
import com.nashi.core.model.StorageSpace
import com.nashi.core.optimizer.SpaceOptimizer
import com.nashi.ui.NashiEmptyState
import com.nashi.ui.NashiScreenHeader
import com.nashi.ui.NashiStatTile
import kotlinx.coroutines.launch

@Composable
fun SpaceScreen(vm: SpaceViewModel = hiltViewModel()) {
    val rooms by vm.rooms.collectAsState()
    var roomId by remember { mutableStateOf("") }
    var spaceId by remember { mutableStateOf("") }
    LaunchedEffect(rooms) { if (roomId.isBlank()) roomId = rooms.firstOrNull()?.id ?: "" }
    val spaces by vm.spacesOf(roomId).collectAsState(initial = emptyList())
    LaunchedEffect(spaces) { if (spaces.none { it.id == spaceId }) spaceId = spaces.firstOrNull()?.id ?: "" }
    val items by vm.itemsOfSpace(roomId, spaceId).collectAsState(initial = emptyList())
    val space = spaces.firstOrNull { it.id == spaceId }
    // 尺寸为 0 的物品只能按默认值摆放, 利用率会被明显低估, 需要提示用户补全
    val missingSize = items.count { it.width <= 0f || it.height <= 0f || it.depth <= 0f }

    var result by remember { mutableStateOf<SpaceOptimizer.Result?>(null) }
    var optimizing by remember { mutableStateOf(false) }
    var show3d by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(spaceId) { result = null }

    Column(
        Modifier
            .statusBarsPadding()
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        NashiScreenHeader(
            title = "空间",
            subtitle = "按真实尺寸预览货架，估算收纳利用率",
        )
        Spacer(Modifier.height(14.dp))

        if (rooms.isEmpty()) {
            NashiEmptyState(
                icon = Icons.Default.ViewModule,
                title = "还没有可预览的空间",
                message = "先在「位置 → 管理」里添加房间和货架。",
            )
            return@Column
        }

        Text(
            "房间",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rooms, key = { it.id }) { r ->
                FilterChip(selected = r.id == roomId, onClick = { roomId = r.id }, label = { Text(r.name) })
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "空间",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(spaces, key = { it.id }) { s ->
                FilterChip(selected = s.id == spaceId, onClick = { spaceId = s.id }, label = { Text(s.name) })
            }
        }
        Spacer(Modifier.height(14.dp))

        if (space == null) {
            NashiEmptyState(
                icon = Icons.Default.ViewModule,
                title = "该房间下暂无空间",
                message = "在「位置 → 管理」里给房间添加货架、收纳箱或抽屉。",
            )
        } else {
            ElevatedCard(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(space.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                items.size.toString() + " 件物品 · " + space.columns + " 列 · " +
                                    fmt(space.width) + "×" + fmt(space.depth) + " cm",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { show3d = !show3d }) {
                            Text(if (show3d) "2D 视图" else "3D 预览")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    ) {
                        if (show3d) {
                            Space3DView(space = space, items = items, result = result)
                        } else {
                            SpaceCanvas(space = space, items = items, result = result)
                        }
                    }
                }
            }

            if (missingSize > 0) {
                Spacer(Modifier.height(8.dp))
                ElevatedCard(
                    Modifier.fillMaxWidth(),
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
                            "有 $missingSize 件物品未填尺寸，预览与利用率按默认尺寸估算；请先在物品详情里填写宽/高/深。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    scope.launch {
                        optimizing = true
                        result = try { vm.optimize(spaceId) } catch (_: Exception) { null }
                        optimizing = false
                    }
                },
                enabled = !optimizing && spaceId.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) {
                if (optimizing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("优化中…")
                } else {
                    Icon(Icons.Default.AutoFixHigh, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("一键优化")
                }
            }

            result?.let { r -> ResultSummary(space = space, result = r, missingSize = missingSize) }
        }
    }
}

@Composable
private fun SpaceCanvas(space: StorageSpace, items: List<Item>, result: SpaceOptimizer.Result?) {
    val cabinet = MaterialTheme.colorScheme.surfaceVariant
    val goods = MaterialTheme.colorScheme.primary
    val placedColor = MaterialTheme.colorScheme.tertiary
    Canvas(Modifier.fillMaxWidth().height(220.dp)) {
        val w = space.width.takeIf { it > 0 } ?: 100f
        val d = space.depth.takeIf { it > 0 } ?: 60f
        drawRect(cabinet, size = size)
        val sx = size.width / w
        val sz = size.height / d
        if (result != null) {
            val cols = maxOf(1, space.columns)
            val shelfW = w / cols
            result.placed.forEach { p ->
                val col = p.shelfId.substringAfterLast("-").toIntOrNull() ?: 0
                val x0 = (col * shelfW + p.x) * sx
                drawRect(
                    placedColor,
                    topLeft = Offset(x0.coerceIn(0f, size.width), 0f),
                    size = Size((p.rw * sx).coerceAtLeast(2f), (p.rd * sz).coerceAtLeast(2f)),
                )
            }
        } else {
            items.forEach { item ->
                val iw = item.width.takeIf { it > 0 } ?: 20f
                val idp = item.depth.takeIf { it > 0 } ?: 20f
                drawRect(
                    goods,
                    topLeft = Offset((item.x * sx).coerceIn(0f, size.width), (item.z * sz).coerceIn(0f, size.height)),
                    size = Size((iw * sx).coerceAtLeast(2f), (idp * sz).coerceAtLeast(2f)),
                )
            }
        }
    }
}

@Composable
private fun ResultSummary(space: StorageSpace, result: SpaceOptimizer.Result, missingSize: Int) {
    val placedVol = result.placed.sumOf { (it.rw * it.rh * it.rd).toDouble() }
    val shelfH = space.shelfHeight.takeIf { it > 0 } ?: 200f
    val totalVol = (space.width.takeIf { it > 0 } ?: 100f) * shelfH * (space.depth.takeIf { it > 0 } ?: 60f)
    val usage = if (totalVol > 0) (placedVol / totalVol * 100).coerceIn(0.0, 100.0) else 0.0
    Spacer(Modifier.height(12.dp))
    ElevatedCard(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "优化结果",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NashiStatTile(
                    value = result.placed.size.toString(),
                    label = "已摆放",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                NashiStatTile(
                    value = result.unshelved.size.toString(),
                    label = "未装下",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                NashiStatTile(
                    value = result.shelvesUsed.toString(),
                    label = "用到的货架",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "利用率估算",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "%.1f%%".format(usage),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                LinearProgressIndicator(
                    progress = { (usage / 100.0).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer,
                    strokeCap = StrokeCap.Round,
                )
            }
            if (missingSize > 0) {
                Text(
                    "注: 其中 $missingSize 件物品缺尺寸, 按默认尺寸参与排布, 利用率仅为估算。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (result.unshelved.isNotEmpty()) {
                Text(
                    "未装下: " + result.unshelved.take(5).joinToString { it.id },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun fmt(v: Float): String = if (v % 1f == 0f) v.toInt().toString() else v.toString()
