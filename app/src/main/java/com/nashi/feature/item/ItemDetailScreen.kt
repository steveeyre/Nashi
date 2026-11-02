package com.nashi.feature.item

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.nashi.core.data.ItemQuery
import com.nashi.ui.NashiInitialBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenHistory: (String) -> Unit,
    vm: ItemDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val loading by vm.loading.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(state?.item?.name ?: "物品详情", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    state?.let { s ->
                        IconButton(onClick = { onEdit(s.item.id) }) { Icon(Icons.Default.Edit, "编辑 " + s.item.name) }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "删除 " + s.item.name) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { pad ->
        val s = state
        if (s == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                if (loading) CircularProgressIndicator()
                else Text("物品不存在或已删除", style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (s.photos.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(s.photos) { path ->
                        AsyncImage(
                            model = path,
                            contentDescription = null,
                            modifier = Modifier.size(150.dp).clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }

            ElevatedCard(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NashiInitialBadge(s.item.name, size = 52)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(s.item.name, style = MaterialTheme.typography.headlineSmall)
                        if (s.item.brand.isNotBlank()) {
                            Text(
                                s.item.brand,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (s.item.barcode.isNotBlank()) {
                            Text(
                                "条码 " + s.item.barcode,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            ElevatedCard(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            "当前数量",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                fmt(s.item.quantity),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                s.item.unit,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 3.dp),
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalIconButton(onClick = { vm.adjust(-1.0) }) {
                            Icon(Icons.Default.Remove, "出库 1")
                        }
                        FilledTonalIconButton(onClick = { vm.adjust(1.0) }) {
                            Icon(Icons.Default.Add, "入库 1")
                        }
                    }
                }
            }

            if (ItemQuery.isLowStock(s.item)) {
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
                            "低于最低库存 " + fmt(s.item.minStock) + " " + s.item.unit + "，需补货",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            ElevatedCard(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    InfoRow("分类", s.categoryName.ifBlank { "未分类" })
                    Divider()
                    InfoRow(
                        "位置",
                        listOf(s.roomName, s.spaceName).filter { it.isNotBlank() }.joinToString(" / ")
                            .ifBlank { "未指定" },
                    )
                    if (s.tagNames.isNotEmpty()) {
                        Divider()
                        InfoRow("标签", s.tagNames.joinToString("、"))
                    }
                    s.fields.forEach { (label, value) ->
                        Divider()
                        InfoRow(label, value)
                    }
                    s.item.expiryDate?.let {
                        Divider()
                        InfoRow("保质期至", date(it))
                    }
                    if (s.item.minStock > 0) {
                        Divider()
                        InfoRow("最低库存", fmt(s.item.minStock) + " " + s.item.unit)
                    }
                    Divider()
                    InfoRow("入库时间", date(s.item.inTime))
                    if (s.item.width > 0f || s.item.height > 0f || s.item.depth > 0f) {
                        Divider()
                        InfoRow(
                            "尺寸",
                            fmt(s.item.width.toDouble()) + " × " + fmt(s.item.height.toDouble()) +
                                " × " + fmt(s.item.depth.toDouble()) + " cm",
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = { onOpenHistory(s.item.id) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) {
                Icon(Icons.Default.History, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("查看该物品流水")
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmDelete && state != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除物品") },
            text = { Text("确定删除「" + state!!.item.name + "」？删除会同步到其他设备。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(onBack)
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            // 合并成单个节点, 读屏与自动化都只能拿到这一条描述
            .semantics(mergeDescendants = true) { contentDescription = "$label $value" },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun fmt(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

private fun date(ms: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(ms))
