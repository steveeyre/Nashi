package com.nashi.feature.fields

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nashi.core.model.CategoryFieldDef
import com.nashi.ui.NashiEmptyState
import java.util.UUID

/** 分类动态字段维护: 字段定义驱动物品编辑页的表单。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryFieldsScreen(onBack: () -> Unit, vm: CategoryFieldsViewModel = hiltViewModel()) {
    val categories by vm.categories.collectAsState()
    val selectedId by vm.selectedCategory.collectAsState()
    val fields by vm.fields.collectAsState()
    var add by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<CategoryFieldDef?>(null) }
    var remove by remember { mutableStateOf<CategoryFieldDef?>(null) }

    LaunchedEffect(categories, selectedId) {
        if (selectedId.isBlank() || categories.none { it.id == selectedId }) {
            categories.firstOrNull()?.let { vm.selectCategory(it.id) }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("分类字段", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            if (selectedId.isNotBlank()) {
                FloatingActionButton(onClick = { add = true }) { Icon(Icons.Default.Add, "新增字段") }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp)) {
            LazyRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories, key = { it.id }) { c ->
                    FilterChip(
                        selected = c.id == selectedId,
                        onClick = { vm.selectCategory(c.id) },
                        label = { Text(c.name) },
                    )
                }
            }
            if (selectedId.isBlank()) {
                NashiEmptyState(
                    icon = Icons.Default.Tune,
                    title = "暂无分类",
                    message = "先到「更多 → 分类管理」新增分类，再为它配置专属字段。",
                )
                return@Column
            }
            if (fields.isEmpty()) {
                NashiEmptyState(
                    icon = Icons.Default.Tune,
                    title = "该分类还没有自定义字段",
                    message = "字段会出现在物品编辑页，例如容量、尺码、开封时间。",
                    actionLabel = "新增字段",
                    onAction = { add = true },
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(fields, key = { it.id }) { def ->
                        ElevatedCard(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        def.label + if (def.required) " *" else "",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        def.key + " · " + def.type +
                                            if (def.optionsCsv.isNotBlank()) " · " + def.optionsCsv else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { edit = def }) { Icon(Icons.Default.Edit, "编辑 " + def.label) }
                                IconButton(onClick = { remove = def }) { Icon(Icons.Default.Delete, "删除 " + def.label) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (add || edit != null) {
        val initial = edit
        FieldDialog(
            initial = initial,
            onDismiss = { add = false; edit = null },
            onConfirm = { key, label, type, required, options ->
                vm.save(
                    CategoryFieldDef(
                        id = initial?.id ?: UUID.randomUUID().toString(),
                        categoryId = selectedId,
                        key = key,
                        label = label,
                        type = type,
                        required = required,
                        optionsCsv = options,
                    )
                )
                add = false
                edit = null
            },
        )
    }
    remove?.let { def ->
        AlertDialog(
            onDismissRequest = { remove = null },
            title = { Text("删除字段") },
            text = { Text("确认删除「${def.label}」? 已填写的字段值会保留在数据库中但不再显示。") },
            confirmButton = { TextButton(onClick = { vm.delete(def.id); remove = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { remove = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun FieldDialog(
    initial: CategoryFieldDef?,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Boolean, String) -> Unit,
) {
    var key by remember(initial) { mutableStateOf(initial?.key ?: "") }
    var label by remember(initial) { mutableStateOf(initial?.label ?: "") }
    var type by remember(initial) { mutableStateOf(initial?.type ?: "text") }
    var required by remember(initial) { mutableStateOf(initial?.required ?: false) }
    var options by remember(initial) { mutableStateOf(initial?.optionsCsv ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增字段" else "编辑字段") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    key,
                    { key = it },
                    label = { Text("字段 key (英文, 如 size)") },
                    singleLine = true,
                    enabled = initial == null,
                )
                OutlinedTextField(label, { label = it }, label = { Text("显示名") }, singleLine = true)
                Text("类型", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("text", "number", "date", "enum", "bool")) { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) })
                    }
                }
                if (type == "enum") {
                    OutlinedTextField(
                        options,
                        { options = it },
                        label = { Text("选项, 逗号分隔 (S,M,L)") },
                        singleLine = true,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = required, onCheckedChange = { required = it })
                    Spacer(Modifier.padding(start = 8.dp))
                    Text("必填")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(key.trim(), label.trim(), type, required, options.trim()) },
                enabled = key.isNotBlank() && label.isNotBlank() &&
                    (type != "enum" || options.isNotBlank()),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
