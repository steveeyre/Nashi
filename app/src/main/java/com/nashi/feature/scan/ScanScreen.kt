package com.nashi.feature.scan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.nashi.core.lookup.Isbn
import com.nashi.core.scan.ReceiptLine
import com.nashi.core.scan.ReceiptParser
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onNewItem: (String) -> Unit,
    onDone: () -> Unit,
    vm: ScanViewModel = hiltViewModel(),
) {
    var tab by remember { mutableStateOf(0) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("扫码 / 小票") },
                actions = { TextButton(onClick = onDone) { Text("完成") } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("扫码") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("小票") })
            }
            when (tab) {
                0 -> ScanTab(vm = vm, onNewItem = onNewItem)
                else -> ReceiptTab(vm = vm, onDone = onDone)
            }
        }
    }
}

@Composable
private fun ScanTab(
    vm: ScanViewModel,
    onNewItem: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted = it }

    var scanned by remember { mutableStateOf("") }
    // 后台线程防抖: 只在新值不同时 post 到主线程
    val lastSent = remember { AtomicReference("") }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val scanner = remember { BarcodeScanning.getClient() }
    val bgExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { runCatching { scanner.close() }; bgExecutor.shutdown() }
    }

    // 书背 ISBN 是 EAN-13(+ 可选定价附加码), 归一化后再查已有物品
    val bookIsbn = remember(scanned) { Isbn.normalize(scanned) }
    val matchedFlow = remember(scanned, bookIsbn) {
        if (scanned.isBlank()) flowOf(null) else vm.lookupByBarcode(bookIsbn ?: scanned)
    }
    val matched by matchedFlow.collectAsState(initial = null)

    if (!granted) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("需要相机权限才能扫码")
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("申请相机权限") }
            }
        }
        return
    }

    var bound by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
            },
            modifier = Modifier.fillMaxWidth().weight(1f),
            update = { pv ->
                if (bound) return@AndroidView
                bound = true
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener(
                    {
                        val provider = future.get()
                        val preview = Preview.Builder().build()
                            .also { it.setSurfaceProvider(pv.surfaceProvider) }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { ia ->
                                ia.setAnalyzer(bgExecutor) { proxy ->
                                    val media = proxy.image
                                    if (media == null) {
                                        proxy.close()
                                        return@setAnalyzer
                                    }
                                    val input = InputImage.fromMediaImage(
                                        media, proxy.imageInfo.rotationDegrees,
                                    )
                                    scanner.process(input)
                                        .addOnSuccessListener { codes ->
                                            val v = codes.firstOrNull()?.rawValue
                                            if (!v.isNullOrBlank() && lastSent.get() != v) {
                                                lastSent.set(v)
                                                mainHandler.post { scanned = v }
                                            }
                                        }
                                        .addOnCompleteListener { proxy.close() }
                                        .addOnFailureListener { proxy.close() }
                                }
                            }
                        runCatching {
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycle,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis,
                            )
                        }
                    },
                    ContextCompat.getMainExecutor(context),
                )
            },
        )
        // 底部结果卡片
        ElevatedCard(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (scanned.isBlank()) {
                    Text("将条码/二维码对准取景框")
                } else {
                    Text("条码: " + scanned)
                    if (bookIsbn != null) Text("书籍 ISBN: " + bookIsbn)
                    if (matched == null) {
                        Text(
                            if (bookIsbn != null) "未找到匹配物品, 新建时会自动填写书名与作者"
                            else "未找到匹配物品, 可新建入库",
                        )
                    } else {
                        Text(matched!!.name + " · 库存 " + matched!!.quantity + " " + matched!!.unit)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                matched?.let { scope.launch { vm.checkout(it.id, -1.0) } }
                            },
                            enabled = matched != null,
                        ) { Text("出库-1") }
                        Button(
                            onClick = {
                                matched?.let { scope.launch { vm.checkout(it.id, 1.0) } }
                            },
                            enabled = matched != null,
                        ) { Text("入库+1") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onNewItem(bookIsbn ?: scanned) }) {
                            Text(if (bookIsbn != null) "新建书籍" else "新建物品")
                        }
                        TextButton(
                            onClick = {
                                scanned = ""
                                lastSent.set("")
                            },
                        ) { Text("继续扫描") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptTab(
    vm: ScanViewModel,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rawText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("拍照或选图后识别") }
    var recognizing by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var categoryId by remember { mutableStateOf("cat-other") }
    val lines = remember { mutableStateListOf<ReceiptRow>() }
    val categories by vm.categories.collectAsState()
    val recognizer = remember {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }
    DisposableEffect(Unit) { onDispose { runCatching { recognizer.close() } } }

    fun recognize(image: InputImage) {
        recognizing = true
        status = "识别中..."
        recognizer.process(image)
            .addOnSuccessListener { v ->
                rawText = v.text
                status = if (v.text.isBlank()) "未识别到文字, 换一张更清晰的图试试" else "识别完成, 可一键解析"
            }
            .addOnFailureListener { e -> status = "识别失败: " + e.message }
            .addOnCompleteListener { recognizing = false }
    }

    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview(),
    ) { bmp ->
        if (bmp == null) {
            status = "拍照取消"
        } else {
            recognize(InputImage.fromBitmap(bmp, 0))
        }
    }
    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) {
            status = "未选择图片"
        } else {
            runCatching { recognize(InputImage.fromFilePath(context, uri)) }
                .onFailure { status = "图片读取失败: " + it.message }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { photoLauncher.launch(null) }, enabled = !recognizing) { Text("拍照") }
            OutlinedButton(onClick = { pickLauncher.launch("image/*") }, enabled = !recognizing) { Text("选图") }
            OutlinedButton(
                onClick = {
                    lines.clear()
                    ReceiptParser.parse(rawText).forEach { lines.add(ReceiptRow.from(it)) }
                    status = if (lines.isEmpty()) {
                        "未解析出商品行, 可换一张清晰图片或手动加行"
                    } else {
                        "已解析 " + lines.size + " 行, 可勾选/改数量后再入库"
                    }
                },
                enabled = rawText.isNotBlank(),
            ) { Text("一键解析") }
        }
        Text(status)
        if (rawText.isNotBlank()) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Text(rawText, modifier = Modifier.padding(12.dp))
            }
        }
        if (lines.isNotEmpty()) {
            Text("入库分类:")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories, key = { it.id }) { c ->
                    FilterChip(
                        selected = categoryId == c.id,
                        onClick = { categoryId = c.id },
                        label = { Text(c.name) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val selectedCount = lines.count { it.selected }
                Text("待入库 " + selectedCount + "/" + lines.size + " 行")
                Spacer(Modifier.width(8.dp))
                val allSelected = selectedCount == lines.size
                TextButton(onClick = {
                    for (i in lines.indices) lines[i] = lines[i].copy(selected = !allSelected)
                }) { Text(if (allSelected) "全不选" else "全选") }
                Spacer(Modifier.weight(1f))
                Text("同名物品会自动合并数量", style = MaterialTheme.typography.bodySmall)
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(300.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(lines) { i, s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = s.selected,
                            onCheckedChange = { lines[i] = s.copy(selected = it) },
                        )
                        OutlinedTextField(
                            value = s.name,
                            onValueChange = { lines[i] = s.copy(name = it) },
                            label = { Text("名称 " + (i + 1)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = s.quantity,
                            onValueChange = { lines[i] = s.copy(quantity = it) },
                            label = { Text("数量") },
                            modifier = Modifier.width(76.dp),
                            singleLine = true,
                        )
                        Column(Modifier.padding(start = 6.dp)) {
                            Text(s.unit, style = MaterialTheme.typography.bodySmall)
                            s.amount?.let {
                                Text("¥" + fmtAmount(it), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        IconButton(onClick = { lines.removeAt(i) }) {
                            Icon(Icons.Default.Close, contentDescription = "删除该行")
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { lines.add(ReceiptRow("", "1", "件", null)) }) { Text("加一行") }
                Button(
                    onClick = {
                        val pending = lines.filter { it.selected }.mapNotNull { it.toLine() }
                            .filter { it.name.isNotEmpty() }
                        if (pending.isEmpty()) {
                            status = "没有勾选可入库的行"
                        } else {
                            scope.launch {
                                importing = true
                                try {
                                    val result = vm.importReceiptLines(pending, categoryId)
                                    status = "已新增 " + result.created + " 件，合并 " + result.merged + " 件"
                                    if (result.created + result.merged > 0) onDone()
                                } finally {
                                    importing = false
                                }
                            }
                        }
                    },
                    enabled = !importing,
                ) { Text(if (importing) "入库中..." else "入库勾选行") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private data class ReceiptRow(
    val name: String,
    val quantity: String,
    val unit: String,
    val amount: Double?,
    val selected: Boolean = true,
) {
    fun toLine(): ReceiptLine? {
        val qty = quantity.toDoubleOrNull() ?: return null
        return ReceiptLine(name.trim(), qty, unit, amount)
    }

    companion object {
        fun from(line: ReceiptLine) = ReceiptRow(
            name = line.name,
            quantity = fmtQty(line.quantity),
            unit = line.unit,
            amount = line.amount,
        )
    }
}

private fun fmtQty(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else String.format("%.1f", v)

private fun fmtAmount(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else String.format("%.2f", v)
