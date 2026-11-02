package com.nashi.feature.location

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nashi.core.model.HomeRoom
import com.nashi.core.model.Item
import com.nashi.core.model.StorageSpace
import com.nashi.ui.NashiEmptyState
import com.nashi.ui.NashiInitialBadge
import com.nashi.ui.NashiScreenHeader

@Composable
fun LocationScreen(
    onManage: () -> Unit = {},
    onItemClick: (String) -> Unit = {},
    vm: LocationViewModel = hiltViewModel(),
) {
    val rooms by vm.rooms.collectAsState()
    var selectedRoomId by remember { mutableStateOf<String?>(null) }
    var selectedSpaceId by remember { mutableStateOf<String?>(null) }

    val selectedRoom = rooms.firstOrNull { it.id == selectedRoomId }
    val spaces by vm.spacesOf(selectedRoomId ?: "").collectAsState(initial = emptyList())
    val selectedSpace = spaces.firstOrNull { it.id == selectedSpaceId }

    Column(Modifier.statusBarsPadding().fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            NashiScreenHeader(
                title = "位置",
                subtitle = "按房间 → 空间 → 物品逐层查找",
                trailing = { TextButton(onClick = onManage) { Text("管理") } },
            )
            Spacer(Modifier.height(10.dp))
            Breadcrumb(
                roomName = selectedRoom?.name,
                spaceName = selectedSpace?.name,
                onAll = { selectedRoomId = null; selectedSpaceId = null },
                onRoom = { selectedSpaceId = null },
            )
            Spacer(Modifier.height(12.dp))
        }

        when {
            selectedRoomId == null -> RoomList(
                rooms = rooms,
                onRoomClick = { selectedRoomId = it },
                onManage = onManage,
            )
            selectedSpaceId == null -> SpaceList(
                roomName = selectedRoom?.name ?: "",
                spaces = spaces,
                items = vm.itemsOfRoom(selectedRoomId!!).collectAsState(initial = emptyList()).value,
                onSpaceClick = { selectedSpaceId = it },
            )
            else -> SpaceItemList(
                spaceName = selectedSpace?.name ?: "空间",
                items = vm.itemsOfSpace(selectedRoomId!!, selectedSpaceId!!)
                    .collectAsState(initial = emptyList()).value,
                onItemClick = onItemClick,
            )
        }
    }
}

@Composable
private fun Breadcrumb(
    roomName: String?,
    spaceName: String?,
    onAll: () -> Unit,
    onRoom: () -> Unit,
) {
    val active = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "全部房间",
                modifier = Modifier.clickable(onClick = onAll).padding(vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = if (roomName == null) active else idle,
            )
            if (roomName != null) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = idle,
                )
                Text(
                    roomName,
                    modifier = Modifier
                        .clickable(enabled = spaceName != null, onClick = onRoom)
                        .padding(vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (spaceName == null) active else idle,
                )
            }
            if (spaceName != null) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = idle,
                )
                Text(
                    spaceName,
                    modifier = Modifier.padding(vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = active,
                )
            }
        }
    }
}

@Composable
private fun RoomList(
    rooms: List<HomeRoom>,
    onRoomClick: (String) -> Unit,
    onManage: () -> Unit,
) {
    if (rooms.isEmpty()) {
        NashiEmptyState(
            icon = Icons.Default.MeetingRoom,
            title = "还没有房间",
            message = "在管理页添加房间与货架，之后就能按位置找东西。",
            actionLabel = "去管理",
            onAction = onManage,
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        items(rooms, key = { it.id }) { room ->
            val subtitle = if (room.width > 0 && room.depth > 0) {
                fmt(room.width) + " × " + fmt(room.depth) + " cm"
            } else {
                "点开查看空间与物品"
            }
            EntityCard(
                icon = Icons.Default.Home,
                title = room.name,
                subtitle = subtitle,
                onClick = { onRoomClick(room.id) },
            )
        }
    }
}

@Composable
private fun SpaceList(
    roomName: String,
    spaces: List<StorageSpace>,
    items: List<Item>,
    onSpaceClick: (String) -> Unit,
) {
    if (spaces.isEmpty()) {
        NashiEmptyState(
            icon = Icons.Default.ViewModule,
            title = "「" + roomName + "」还没有空间",
            message = "在管理页给这个房间添加货架、收纳箱或抽屉。",
        )
        return
    }
    val counts = remember(items) { items.groupBy { it.spaceId }.mapValues { it.value.size } }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            spaces.size.toString() + " 个空间 · " + items.size + " 件物品",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(spaces, key = { it.id }) { space ->
                EntityCard(
                    icon = Icons.Default.ViewModule,
                    title = space.name,
                    subtitle = spaceTypeLabel(space.type) + " · " + (counts[space.id] ?: 0) + " 件物品",
                    onClick = { onSpaceClick(space.id) },
                )
            }
        }
    }
}

@Composable
private fun SpaceItemList(
    spaceName: String,
    items: List<Item>,
    onItemClick: (String) -> Unit,
) {
    if (items.isEmpty()) {
        NashiEmptyState(
            icon = Icons.Default.ViewModule,
            title = "「" + spaceName + "」还没有物品",
            message = "在物品页新增时选择这个位置即可。",
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Text(
                spaceName + " · " + items.size + " 件物品",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
        }
        items(items, key = { it.id }) { item ->
            ElevatedCard(
                Modifier
                    .fillMaxWidth()
                    .clickable { onItemClick(item.id) },
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NashiInitialBadge(item.name, size = 38)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            fmtQty(item.quantity) + " " + item.unit,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EntityCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ElevatedCard(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .padding(0.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun spaceTypeLabel(type: String): String = when (type) {
    "box" -> "收纳箱"
    "drawer" -> "抽屉"
    "bookcase" -> "书架"
    else -> "货架"
}

private fun fmt(v: Float): String = if (v % 1f == 0f) v.toInt().toString() else v.toString()

private fun fmtQty(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
