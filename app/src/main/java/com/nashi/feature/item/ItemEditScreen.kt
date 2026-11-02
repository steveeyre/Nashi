package com.nashi.feature.item

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.nashi.core.lookup.BookLookupResult
import com.nashi.core.lookup.Isbn
import com.nashi.core.media.PhotoStore
import com.nashi.core.model.CategoryFieldDef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// 物品表单: 通用字段 + CategoryFieldDef 驱动的动态字段 + 拍照 + 位置选择
// inTime 新建自动=当前, 保质期仅食品药品必填(字段定义 required 控制)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditScreen(
    itemId: String = "",
    barcode: String = "",
    onSaved: () -> Unit,
    vm: ItemEditViewModel = hiltViewModel(),
) {
    val scope = rememberCoroutineScope()
    // 扫码入库透传的条码: 在 state 声明遮蔽参数名前先捕获
    val initialBarcode = barcode
    var name by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf("件") }
    var barcode by remember(initialBarcode) { mutableStateOf(initialBarcode) }
    var roomId by remember { mutableStateOf("") }
    var spaceId by remember { mutableStateOf("") }
    var expiryDate by remember { mutableStateOf<Long?>(null) }
    var minStock by remember { mutableStateOf("") }
    var width by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var depth by remember { mutableStateOf("") }
    var photos by remember { mutableStateOf<List<String>>(emptyList()) }
    var dyn by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loaded by remember { mutableStateOf(itemId.isBlank()) }
    var bookState by remember { mutableStateOf<BookLookupResult?>(null) }
    var bookLoading by remember { mutableStateOf(false) }
    val isbn = remember(barcode) { Isbn.normalize(barcode) }

    suspend fun applyBookLookup() {
        bookLoading = true
        try {
            bookState = vm.lookupBook(barcode)
        } finally {
            bookLoading = false
        }
        (bookState as? BookLookupResult.Found)?.let { found ->
            // 只补空字段, 不覆盖用户已经输入的内容
            if (name.isBlank()) name = found.meta.title
            if (brand.isBlank()) brand = found.meta.authors
            if (unit == "件") unit = "本"
        }
    }

    // 新建时扫到 ISBN 直接联网取书名/作者; 编辑已有物品不自动请求
    LaunchedEffect(isbn) {
        if (itemId.isBlank() && isbn != null) applyBookLookup()
    }

    LaunchedEffect(itemId) {
        if (itemId.isNotBlank()) {
            val (item, vals) = vm.loadItem(itemId)
            item?.let {
                name = it.name; categoryId = it.categoryId; brand = it.brand
                quantity = it.quantity.toString(); unit = it.unit; barcode = it.barcode
                roomId = it.roomId; spaceId = it.spaceId
                expiryDate = it.expiryDate
                minStock = if (it.minStock > 0) it.minStock.toString() else ""
                width = if (it.width > 0f) fmtDim(it.width) else ""
                height = if (it.height > 0f) fmtDim(it.height) else ""
                depth = if (it.depth > 0f) fmtDim(it.depth) else ""
                photos = it.photoPathsCsv.split("|").filter(String::isNotBlank)
            }
            dyn = vals
            loaded = true
        }
    }

    val pendingPhoto = remember { mutableStateOf<File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) pendingPhoto.value?.let { file ->
            photos = photos + file.absolutePath
            // 原图动辄数 MB: 后台压缩到长边 1600 并摆正, 界面先用原文件显示
            scope.launch(Dispatchers.IO) { PhotoStore.compressInPlace(file) }
        }
    }

    val defs by vm.fieldDefsOf(categoryId).collectAsState(initial = emptyList())
    val spaces by vm.spacesOf(roomId).collectAsState(initial = emptyList())
    val canSave = name.isNotBlank() && categoryId.isNotBlank() &&
        defs.filter { it.required }.all { (dyn[it.key] ?: "").isNotBlank() || (it.key == "expiry" && expiryDate != null) }

    LaunchedEffect(itemId, vm.categories) {
        if (itemId.isBlank() && categoryId.isBlank() && vm.categories.isNotEmpty()) {
            categoryId = vm.categories.firstOrNull { it.id == "cat-other" }?.id ?: vm.categories.first().id
        }
    }

    val snackbar = remember { SnackbarHostState() }
    Scaffold(
        topBar = { TopAppBar(title = { Text(if (itemId.isBlank()) "物品入库" else "编辑物品") }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (!canSave) {
                        val msg = when {
                            name.isBlank() -> "请填写名称"
                            categoryId.isBlank() -> "请选择分类"
                            else -> "请填写必填字段"
                        }
                        scope.launch { snackbar.showSnackbar(msg) }
                        return@ExtendedFloatingActionButton
                    }
                    scope.launch {
                        vm.save(
                            itemId.ifBlank { null }, name.trim(), categoryId, brand.trim(),
                            quantity.toDoubleOrNull() ?: 1.0, unit.trim().ifBlank { "件" },
                            barcode.trim(), photos, roomId, spaceId,
                            expiryDate ?: dyn["expiry"]?.toLongOrNull(),
                            minStock.toDoubleOrNull() ?: 0.0, dyn,
                            width.toFloatOrNull() ?: 0f,
                            height.toFloatOrNull() ?: 0f,
                            depth.toFloatOrNull() ?: 0f,
                        )
                        onSaved()
                    }
                },
            ) { Text("保存入库") }
        },
    ) { pad ->
        if (!loaded) { Box(Modifier.padding(pad).fillMaxSize()) { CircularProgressIndicator() }; return@Scaffold }
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), Arrangement.spacedBy(10.dp)) {
            CategoryRow(vm.categories, categoryId) { categoryId = it }
            OutlinedTextField(name, { name = it }, label = { Text("名称 *") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    brand, { brand = it },
                    label = { Text(if (isbn != null) "品牌 / 作者" else "品牌") },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(quantity, { quantity = it }, label = { Text("数量") }, modifier = Modifier.weight(1f))
                OutlinedTextField(unit, { unit = it }, label = { Text("单位") }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(
                barcode, { barcode = it },
                label = { Text(if (isbn != null) "条码 · ISBN" else "条码") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (isbn != null) {
                BookLookupRow(
                    loading = bookLoading,
                    state = bookState,
                    onRetry = { scope.launch { applyBookLookup() } },
                )
            }
            OutlinedTextField(
                minStock, { minStock = it }, label = { Text("最低库存(低于提醒补货)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("尺寸 cm (用于空间优化/3D 摆放)", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(width, { width = it }, label = { Text("宽") }, modifier = Modifier.weight(1f))
                OutlinedTextField(height, { height = it }, label = { Text("高") }, modifier = Modifier.weight(1f))
                OutlinedTextField(depth, { depth = it }, label = { Text("深") }, modifier = Modifier.weight(1f))
            }
            ExpiryRow(expiryDate) { expiryDate = it }
            Text("动态字段", style = MaterialTheme.typography.titleSmall)
            defs.forEach { def ->
                if (def.key != "expiry" || def.type != "date") DynField(def, dyn[def.key] ?: "") { dyn = dyn + (def.key to it) }
            }
            Text("位置（不选则记为未指定）", style = MaterialTheme.typography.titleSmall)
            if (vm.rooms.isEmpty()) {
                Text("暂无房间，可先在「位置」页新建房间与空间", style = MaterialTheme.typography.bodySmall)
            } else {
                RoomRow(vm.rooms, roomId) { roomId = it; spaceId = "" }
                if (roomId.isNotBlank()) SpaceRow(spaces, spaceId) { spaceId = it }
            }
            if (vm.tags.isNotEmpty()) {
                Text("标签", style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vm.tags, key = { it.id }) { tag ->
                        FilterChip(
                            selected = tag.id in vm.selectedTagIds,
                            onClick = { vm.toggleTag(tag.id) },
                            label = { Text(tag.name) },
                        )
                    }
                }
            }
            Text("照片", style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(photos) { p ->
                    Box(Modifier.size(96.dp)) {
                        AsyncImage(
                            model = p, contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        IconButton(
                            onClick = { photos = photos - p; PhotoStore.delete(p) },
                            modifier = Modifier.align(Alignment.TopEnd).size(28.dp),
                        ) { Icon(Icons.Default.Close, "删除照片") }
                    }
                }
            }
            PhotoButton { file, uri -> pendingPhoto.value = file; cameraLauncher.launch(uri) }
            Spacer(Modifier.height(72.dp))
        }
    }
}

@Composable
private fun CategoryRow(cats: List<com.nashi.core.model.Category>, sel: String, onSel: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(cats, key = { it.id }) {
            FilterChip(selected = sel == it.id, onClick = { onSel(it.id) }, label = { Text(it.name) })
        }
    }
}

@Composable
private fun RoomRow(rooms: List<com.nashi.core.model.HomeRoom>, sel: String, onSel: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(selected = sel.isBlank(), onClick = { onSel("") }, label = { Text("未指定") })
        }
        items(rooms, key = { it.id }) {
            FilterChip(selected = sel == it.id, onClick = { onSel(it.id) }, label = { Text(it.name) })
        }
    }
}

@Composable
private fun SpaceRow(spaces: List<com.nashi.core.model.StorageSpace>, sel: String, onSel: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(selected = sel.isBlank(), onClick = { onSel("") }, label = { Text("整个房间") })
        }
        items(spaces, key = { it.id }) {
            FilterChip(selected = sel == it.id, onClick = { onSel(it.id) }, label = { Text(it.name) })
        }
    }
}

@Composable
private fun DynField(def: CategoryFieldDef, value: String, onChange: (String) -> Unit) {
    when (def.type) {
        "enum" -> {
            val opts = def.optionsCsv.split(",").filter { it.isNotBlank() }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(opts) { o -> FilterChip(selected = value == o, onClick = { onChange(o) }, label = { Text(o) }) }
            }
        }
        "bool" -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = value == "是", onClick = { onChange("是") }, label = { Text("是") })
                FilterChip(selected = value == "否", onClick = { onChange("否") }, label = { Text("否") })
            }
        }
        else -> OutlinedTextField(
            value, onChange,
            label = { Text(def.label + if (def.required) " *" else "") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ExpiryRow(expiry: Long?, onChange: (Long?) -> Unit) {
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.CHINA) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            expiry?.let { fmt.format(Date(it)) } ?: "",
            {},
            readOnly = true, label = { Text("保质期/有效期") }, modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onChange(System.currentTimeMillis() + 30L * 86400_000) }) { Text("+30天") }
        if (expiry != null) TextButton(onClick = { onChange(null) }) { Text("清除") }
    }
}

@Composable
private fun PhotoButton(onLaunch: (File, Uri) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Button(onClick = {
        val f = File(File(ctx.filesDir, "photos").apply { mkdirs() }, System.currentTimeMillis().toString() + ".jpg")
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
        onLaunch(f, uri)
    }) { Text("拍照上传") }
}

@Composable
private fun BookLookupRow(loading: Boolean, state: BookLookupResult?, onRetry: () -> Unit) {
    val text = when {
        loading -> "正在联网查询书籍信息…"
        state is BookLookupResult.Found -> "已自动填写书名与作者: " + state.meta.title
        state == BookLookupResult.NotFound -> "未查到这本书, 可手动填写"
        state == BookLookupResult.Unavailable -> "联网查询失败, 可检查网络后重试"
        else -> "扫到 ISBN, 将自动填写书名/作者"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        TextButton(onClick = onRetry, enabled = !loading) { Text("重新查询") }
    }
}

private fun fmtDim(v: Float): String =
    if (v % 1f == 0f) v.toInt().toString() else String.format("%.1f", v)
