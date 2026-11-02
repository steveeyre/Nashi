package com.nashi.feature.place

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.ViewModule
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import com.nashi.core.model.HomeRoom
import com.nashi.core.model.StorageSpace
import com.nashi.ui.NashiEmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceScreen(onBack: () -> Unit, vm: PlaceViewModel = hiltViewModel()) {
    val rooms by vm.rooms.collectAsState()
    val status by vm.status.collectAsState()
    val snacks = remember { SnackbarHostState() }

    var selectedRoomId by remember { mutableStateOf("") }
    LaunchedEffect(rooms) {
        if (rooms.none { it.id == selectedRoomId }) selectedRoomId = rooms.firstOrNull()?.id ?: ""
    }
    val selectedRoom = rooms.firstOrNull { it.id == selectedRoomId }
    val spaces by vm.spacesOf(selectedRoomId).collectAsState(initial = emptyList())

    var addRoom by remember { mutableStateOf(false) }
    var editRoom by remember { mutableStateOf<HomeRoom?>(null) }
    var deleteRoom by remember { mutableStateOf<HomeRoom?>(null) }
    var addSpace by remember { mutableStateOf(false) }
    var editSpace by remember { mutableStateOf<StorageSpace?>(null) }
    var deleteSpace by remember { mutableStateOf<StorageSpace?>(null) }

    LaunchedEffect(status) {
        if (status.isNotBlank()) {
            snacks.showSnackbar(status)
            vm.clearStatus()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("房间与空间", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                actions = {
                    IconButton(onClick = { addRoom = true }) { Icon(Icons.Default.Add, "新增房间") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snacks) },
        floatingActionButton = {
            if (selectedRoom != null) {
                FloatingActionButton(onClick = { addSpace = true }) { Icon(Icons.Default.Add, "新增空间") }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp)) {
            if (rooms.isEmpty()) {
                NashiEmptyState(
                    icon = Icons.Default.MeetingRoom,
                    title = "还没有房间",
                    message = "先建一个房间（如客厅、储物间），再往里添加货架或收纳箱。",
                    actionLabel = "新增房间",
                    onAction = { addRoom = true },
                )
                return@Column
            }
            LazyRow(
                Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(rooms, key = { it.id }) { room ->
                    FilterChip(
                        selected = room.id == selectedRoomId,
                        onClick = { selectedRoomId = room.id },
                        label = { Text(room.name) },
                    )
                }
            }
            selectedRoom?.let { room ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        buildString {
                            append(room.name)
                            if (room.width > 0 && room.depth > 0) {
                                append(" · ${fmt(room.width)}×${fmt(room.height)}×${fmt(room.depth)} cm")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row {
                        IconButton(onClick = { editRoom = room }) { Icon(Icons.Default.Edit, "编辑房间") }
                        IconButton(onClick = { deleteRoom = room }) { Icon(Icons.Default.Delete, "删除房间") }
                    }
                }
                Text("空间 ${spaces.size} 个", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                if (spaces.isEmpty()) {
                    NashiEmptyState(
                        icon = Icons.Default.ViewModule,
                        title = "该房间下暂无空间",
                        message = "用右下角 + 添加货架、收纳箱或抽屉。",
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(spaces, key = { it.id }) { space ->
                            ElevatedCard(
                                Modifier.fillMaxWidth(),
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                ),
                                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.ViewModule,
                                            contentDescription = null,
                                            modifier = Modifier.size(22.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(space.name, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "${space.type} · ${fmt(space.width)}×${fmt(space.shelfHeight)}×${fmt(space.depth)} cm · ${space.columns} 列",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    IconButton(onClick = { editSpace = space }) { Icon(Icons.Default.Edit, "编辑空间") }
                                    IconButton(onClick = { deleteSpace = space }) { Icon(Icons.Default.Delete, "删除空间") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (addRoom || editRoom != null) {
        val initial = editRoom
        RoomDialog(
            initial = initial,
            onDismiss = { addRoom = false; editRoom = null },
            onConfirm = { name, w, h, d ->
                vm.saveRoom(initial?.id, name, w, h, d)
                addRoom = false
                editRoom = null
            },
        )
    }
    if (addSpace || editSpace != null) {
        val initial = editSpace
        SpaceDialog(
            initial = initial,
            onDismiss = { addSpace = false; editSpace = null },
            onConfirm = { name, type, w, d, shelfH, cols ->
                vm.saveSpace(initial?.id, selectedRoomId, name, type, w, d, shelfH, cols)
                addSpace = false
                editSpace = null
            },
        )
    }
    deleteRoom?.let { room ->
        ConfirmDialog(
            title = "删除房间",
            text = "确认删除「${room.name}」? 其下空间会一并删除, 物品记录保留。",
            onDismiss = { deleteRoom = null },
            onConfirm = { vm.deleteRoom(room); deleteRoom = null },
        )
    }
    deleteSpace?.let { space ->
        ConfirmDialog(
            title = "删除空间",
            text = "确认删除「${space.name}」? 物品记录保留。",
            onDismiss = { deleteSpace = null },
            onConfirm = { vm.deleteSpace(space); deleteSpace = null },
        )
    }
}

@Composable
private fun RoomDialog(
    initial: HomeRoom?,
    onDismiss: () -> Unit,
    onConfirm: (String, Float, Float, Float) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var width by remember(initial) { mutableStateOf(numText(initial?.width)) }
    var height by remember(initial) { mutableStateOf(numText(initial?.height)) }
    var depth by remember(initial) { mutableStateOf(numText(initial?.depth)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增房间" else "编辑房间") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("房间名") }, singleLine = true)
                NumberField("宽 cm", width) { width = it }
                NumberField("高 cm", height) { height = it }
                NumberField("深 cm", depth) { depth = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, width.toFloatOrNull() ?: 0f, height.toFloatOrNull() ?: 0f, depth.toFloatOrNull() ?: 0f) },
                enabled = name.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun SpaceDialog(
    initial: StorageSpace?,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Float, Float, Float, Int) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var type by remember(initial) { mutableStateOf(initial?.type ?: "shelf") }
    var width by remember(initial) { mutableStateOf(numText(initial?.width)) }
    var depth by remember(initial) { mutableStateOf(numText(initial?.depth)) }
    var shelfHeight by remember(initial) { mutableStateOf(numText(initial?.shelfHeight)) }
    var columns by remember(initial) { mutableStateOf((initial?.columns ?: 1).toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增空间" else "编辑空间") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("空间名") }, singleLine = true)
                OutlinedTextField(type, { type = it }, label = { Text("类型 shelf/box/drawer/bookcase") }, singleLine = true)
                NumberField("宽 cm", width) { width = it }
                NumberField("深 cm", depth) { depth = it }
                NumberField("层高 cm", shelfHeight) { shelfHeight = it }
                NumberField("列数", columns) { columns = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        name,
                        type.ifBlank { "shelf" },
                        width.toFloatOrNull() ?: 0f,
                        depth.toFloatOrNull() ?: 0f,
                        shelfHeight.toFloatOrNull() ?: 0f,
                        columns.toIntOrNull() ?: 1,
                    )
                },
                enabled = name.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value,
        onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun ConfirmDialog(title: String, text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("删除") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun numText(v: Float?): String = if (v == null || v <= 0f) "" else fmt(v)

private fun fmt(v: Float): String =
    if (v % 1f == 0f) v.toInt().toString() else String.format("%.1f", v)
