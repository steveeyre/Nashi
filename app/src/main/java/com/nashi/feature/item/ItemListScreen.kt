package com.nashi.feature.item

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.nashi.core.data.ItemQuery
import com.nashi.core.data.ItemSort
import com.nashi.core.model.Item
import com.nashi.ui.NashiEmptyState
import com.nashi.ui.NashiInitialBadge
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemListScreen(
    onAdd: () -> Unit,
    onOpenItem: (String) -> Unit,
    onScan: () -> Unit = {},
    vm: ItemListViewModel = hiltViewModel(),
) {
    val filter by vm.filter.collectAsState()
    val content by vm.content.collectAsState()
    val selection by vm.selection.collectAsState()
    val categories by vm.categories.collectAsState()
    val rooms by vm.rooms.collectAsState()
    val tags by vm.tags.collectAsState()
    val spaces = vm.spaces
    val snacks = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<Item?>(null) }
    var showFilters by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    var selectMode by remember { mutableStateOf(false) }
    var bulkAction by remember { mutableStateOf("") }
    var targetCategory by remember { mutableStateOf("") }
    var targetRoom by remember { mutableStateOf("") }
    var targetSpace by remember { mutableStateOf("") }

    val filterActive = filter.keyword.isNotBlank() || filter.categoryId.isNotBlank() ||
        filter.roomId.isNotBlank() || filter.tagId.isNotBlank() || filter.lowStockOnly
    fun clearFilters() {
        vm.onQuery("")
        vm.onCategory("")
        vm.onRoom("")
        vm.onTag("")
        vm.onLowStockOnly(false)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (selectMode) "已选 " + selection.size + " 件" else "物品",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (!selectMode) {
                            Text(
                                content.total.toString() + " 件在库",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    if (selectMode) {
                        TextButton(onClick = vm::selectAllVisible) { Text("全选") }
                        TextButton(onClick = { selectMode = false; vm.clearSelection() }) { Text("完成") }
                    } else {
                        IconButton(onClick = onScan) {
                            Icon(Icons.Default.QrCodeScanner, "扫码")
                        }
                        TextButton(onClick = { selectMode = true }) { Text("多选") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snacks) },
        floatingActionButton = {
            if (!selectMode) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("新增物品") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            if (!selectMode) {
                OutlinedTextField(
                    value = filter.keyword,
                    onValueChange = vm::onQuery,
                    placeholder = { Text("搜索名称 / 品牌 / 条码") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = CircleShape,
                    leadingIcon = {
                        Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingIcon = {
                        if (filter.keyword.isNotEmpty()) {
                            IconButton(onClick = { vm.onQuery("") }) {
                                Icon(Icons.Default.Clear, "清空搜索")
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(
                        onClick = { showFilters = !showFilters },
                        label = { Text(if (showFilters) "收起筛选" else "筛选") },
                        leadingIcon = { Icon(Icons.Default.Tune, null, modifier = Modifier.size(16.dp)) },
                    )
                    if (filter.lowStockOnly) {
                        AssistChip(
                            onClick = { vm.onLowStockOnly(false) },
                            label = { Text("仅低库存") },
                            trailingIcon = { Icon(Icons.Default.Clear, null, modifier = Modifier.size(14.dp)) },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Row {
                        AssistChip(
                            onClick = { sortOpen = true },
                            label = { Text(filter.sort.label) },
                            leadingIcon = { Icon(Icons.Default.Sort, null, modifier = Modifier.size(16.dp)) },
                        )
                        DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                            ItemSort.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = { Text(sort.label) },
                                    onClick = { vm.onSort(sort); sortOpen = false },
                                )
                            }
                        }
                    }
                }
                if (showFilters) {
                    FilterSection(
                        categories = categories.map { it.id to it.name },
                        rooms = rooms.map { it.id to it.name },
                        tags = tags.map { it.id to it.name },
                        selectedCategory = filter.categoryId,
                        selectedRoom = filter.roomId,
                        selectedTag = filter.tagId,
                        lowStockOnly = filter.lowStockOnly,
                        onCategory = vm::onCategory,
                        onRoom = vm::onRoom,
                        onTag = vm::onTag,
                        onLowStockOnly = vm::onLowStockOnly,
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { bulkAction = "delete" },
                        enabled = selection.isNotEmpty(),
                    ) { Text("删除") }
                    OutlinedButton(
                        onClick = {
                            targetRoom = ""
                            targetSpace = ""
                            vm.loadSpaces("")
                            bulkAction = "move"
                        },
                        enabled = selection.isNotEmpty(),
                    ) { Text("移动") }
                    OutlinedButton(
                        onClick = { targetCategory = ""; bulkAction = "category" },
                        enabled = selection.isNotEmpty(),
                    ) { Text("改分类") }
                }
            }
            Spacer(Modifier.height(8.dp))
            val hidden = content.total - content.items.size
            if (hidden > 0) {
                Text(
                    "显示前 " + content.items.size + " 件，还有 " + hidden + " 件",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
            }
            if (content.total == 0) {
                NashiEmptyState(
                    icon = Icons.Default.Inventory2,
                    title = if (filterActive) "没有匹配的物品" else "还没有物品",
                    message = if (filterActive) {
                        "换个关键词或清空筛选条件再试。"
                    } else {
                        "扫码或手动添加第一件物品，之后按房间与空间逐层查找。"
                    },
                    modifier = Modifier.weight(1f),
                    actionLabel = if (filterActive) "清除筛选" else "添加物品",
                    onAction = if (filterActive) ({ clearFilters() }) else onAdd,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    // 给 FAB 留出空间, 横屏短内容时最后一条不被遮挡
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    items(content.items, key = { it.id }) { item ->
                        val categoryName = categories.firstOrNull { it.id == item.categoryId }?.name
                        ItemCard(
                            item = item,
                            categoryName = categoryName,
                            selectMode = selectMode,
                            selected = item.id in selection,
                            onOpen = {
                                if (selectMode) vm.toggleSelection(item.id) else onOpenItem(item.id)
                            },
                            onToggle = { vm.toggleSelection(item.id) },
                            onDelete = { pendingDelete = item },
                        )
                    }
                    if (hidden > 0) {
                        item {
                            OutlinedButton(
                                onClick = vm::loadMore,
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Text("加载更多（还有 " + hidden + " 件）")
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除物品") },
            text = { Text("确定删除「" + target.name + "」？删除会同步到其他设备，可在回收站恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        vm.delete(target)
                        val result = snacks.showSnackbar(
                            message = "已删除 " + target.name,
                            actionLabel = "撤销",
                            duration = SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) vm.undoDelete(target)
                    }
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }

    if (bulkAction == "delete") {
        AlertDialog(
            onDismissRequest = { bulkAction = "" },
            title = { Text("批量删除") },
            text = { Text("将删除选中的 " + selection.size + " 件物品，可在回收站恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.bulkDelete()
                    bulkAction = ""
                    selectMode = false
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { bulkAction = "" }) { Text("取消") } },
        )
    }

    if (bulkAction == "move") {
        AlertDialog(
            onDismissRequest = { bulkAction = "" },
            title = { Text("移动到") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("房间", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = targetRoom.isEmpty(),
                                onClick = { targetRoom = ""; targetSpace = ""; vm.loadSpaces("") },
                                label = { Text("未分配") },
                            )
                        }
                        items(rooms, key = { it.id }) { room ->
                            FilterChip(
                                selected = targetRoom == room.id,
                                onClick = {
                                    targetRoom = room.id
                                    targetSpace = ""
                                    vm.loadSpaces(room.id)
                                },
                                label = { Text(room.name) },
                            )
                        }
                    }
                    if (spaces.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("空间", style = MaterialTheme.typography.labelMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = targetSpace.isEmpty(),
                                    onClick = { targetSpace = "" },
                                    label = { Text("整个房间") },
                                )
                            }
                            items(spaces, key = { it.id }) { space ->
                                FilterChip(
                                    selected = targetSpace == space.id,
                                    onClick = { targetSpace = space.id },
                                    label = { Text(space.name) },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.bulkMove(targetRoom, targetSpace)
                    bulkAction = ""
                    selectMode = false
                }) { Text("移动") }
            },
            dismissButton = { TextButton(onClick = { bulkAction = "" }) { Text("取消") } },
        )
    }

    if (bulkAction == "category") {
        AlertDialog(
            onDismissRequest = { bulkAction = "" },
            title = { Text("改为分类") },
            text = {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories, key = { it.id }) { category ->
                        FilterChip(
                            selected = targetCategory == category.id,
                            onClick = { targetCategory = category.id },
                            label = { Text(category.name) },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (targetCategory.isNotEmpty()) vm.bulkCategory(targetCategory)
                        bulkAction = ""
                        selectMode = false
                    },
                    enabled = targetCategory.isNotEmpty(),
                ) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { bulkAction = "" }) { Text("取消") } },
        )
    }
}

@Composable
private fun ItemCard(
    item: Item,
    categoryName: String?,
    selectMode: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    ElevatedCard(
        Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = (if (selectMode) "选择 " else "打开 ") + item.name
            }
            .clickable(onClick = onOpen),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectMode) {
                Checkbox(checked = selected, onCheckedChange = { onToggle() })
                Spacer(Modifier.width(4.dp))
            } else {
                ItemThumb(item)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOfNotNull(item.brand.ifBlank { null }, categoryName).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (ItemQuery.isLowStock(item)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "低于最低库存 " + fmtQty(item.minStock) + " " + item.unit + "，需补货",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    fmtQty(item.quantity) + " " + item.unit,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            if (!selectMode) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        "删除 " + item.name,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemThumb(item: Item) {
    val path = remember(item.photoPathsCsv) {
        item.photoPathsCsv.split('|').firstOrNull { it.isNotBlank() }
    }
    if (path != null) {
        val fallback = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
        AsyncImage(
            model = File(path),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = fallback,
            error = fallback,
            modifier = Modifier.size(44.dp).clip(CircleShape),
        )
    } else {
        NashiInitialBadge(item.name)
    }
}

@Composable
private fun FilterSection(
    categories: List<Pair<String, String>>,
    rooms: List<Pair<String, String>>,
    tags: List<Pair<String, String>>,
    selectedCategory: String,
    selectedRoom: String,
    selectedTag: String,
    lowStockOnly: Boolean,
    onCategory: (String) -> Unit,
    onRoom: (String) -> Unit,
    onTag: (String) -> Unit,
    onLowStockOnly: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChipsRow("分类", categories, selectedCategory, onCategory)
        FilterChipsRow("房间", rooms, selectedRoom, onRoom)
        if (tags.isNotEmpty()) FilterChipsRow("标签", tags, selectedTag, onTag)
        Row {
            FilterChip(
                selected = lowStockOnly,
                onClick = { onLowStockOnly(!lowStockOnly) },
                label = { Text("仅低库存") },
            )
        }
    }
}

@Composable
private fun FilterChipsRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        item {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 4.dp))
        }
        item {
            FilterChip(selected = selected.isEmpty(), onClick = { onSelect("") }, label = { Text("全部") })
        }
        items(options, key = { it.first }) { (id, name) ->
            FilterChip(selected = selected == id, onClick = { onSelect(id) }, label = { Text(name) })
        }
    }
}

private fun fmtQty(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
