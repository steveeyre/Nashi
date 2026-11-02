package com.nashi.feature.trash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nashi.core.model.Item
import com.nashi.ui.NashiEmptyState
import com.nashi.ui.NashiInitialBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(onBack: () -> Unit, vm: TrashViewModel = hiltViewModel()) {
    val items by vm.items.collectAsState()
    var pendingPurge by remember { mutableStateOf<Item?>(null) }
    val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("回收站", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { pad ->
        if (items.isEmpty()) {
            Column(Modifier.padding(pad).fillMaxSize()) {
                NashiEmptyState(
                    icon = Icons.Default.DeleteOutline,
                    title = "回收站是空的",
                    message = "删除的物品会先来到这里，可随时恢复。",
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "删除的物品可恢复；彻底删除后无法找回，且会同步到其他设备。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
            }
            items(items, key = { it.id }) { item ->
                ElevatedCard(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NashiInitialBadge(item.name, size = 38)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                fmtQty(item.quantity) + " " + item.unit +
                                    " · 删除于 " + fmt.format(Date(item.updatedAt)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { vm.restore(item) }) {
                            Icon(Icons.Default.Restore, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("恢复")
                        }
                        IconButton(onClick = { pendingPurge = item }) {
                            Icon(
                                Icons.Default.DeleteForever,
                                "彻底删除 " + item.name,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }

    pendingPurge?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingPurge = null },
            title = { Text("彻底删除") },
            text = { Text("「" + target.name + "」将永久删除，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingPurge = null
                    vm.purge(target)
                }) { Text("彻底删除") }
            },
            dismissButton = { TextButton(onClick = { pendingPurge = null }) { Text("取消") } },
        )
    }
}

private fun fmtQty(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
