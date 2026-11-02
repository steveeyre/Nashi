package com.nashi.feature.more

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nashi.R
import com.nashi.core.io.BackupPasswordRequired
import com.nashi.ui.NashiNavRow
import java.io.BufferedInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * 基础功能6(导入导出)+11(分类标签管理).
 * 分类/标签管理行 + 品牌列表(statByBrand 复用) + 导入导出按钮组.
 * 文件读写经 SAF: OpenDocument 读入, CreateDocument 写出.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onOpenFields: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenTrash: () -> Unit = {},
    vm: MoreViewModel = hiltViewModel(),
) {
    val tags by vm.tags.collectAsState()
    val categories by vm.categories.collectAsState()
    val brands by vm.brands.collectAsState()
    val status by vm.status.collectAsState()
    val sync by vm.syncSettings.collectAsState()
    val members by vm.members.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snacks = remember { SnackbarHostState() }
    var pendingExport by remember { mutableStateOf("") }
    var serverUrl by remember(sync.serverUrl) { mutableStateOf(sync.serverUrl) }
    var inviteCode by remember { mutableStateOf("") }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf("") }
    var exportPassword2 by remember { mutableStateOf("") }
    var exportPasswordError by remember { mutableStateOf("") }
    var pendingZipUri by remember { mutableStateOf<Uri?>(null) }
    var zipPassword by remember { mutableStateOf("") }
    var showMembers by remember { mutableStateOf(false) }

    LaunchedEffect(status) {
        if (status.isNotBlank()) {
            snacks.showSnackbar(status)
            vm.clearStatus()
        }
    }

    // 加密备份: 先不带口令试读, 发现是加密包就留着 URI 弹框, 拿到口令后重试。
    suspend fun importZip(uri: Uri, password: String?) {
        runCatching {
            val input = ctx.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("无法读取所选文件")
            BufferedInputStream(input).use { buf -> vm.importZipStream(buf, password) }
        }.onSuccess { vm.onImportZipDone(it) }
            .onFailure { e ->
                if (e is BackupPasswordRequired) {
                    zipPassword = ""
                    pendingZipUri = uri
                } else {
                    vm.onImportFailed(e)
                }
            }
    }

    val readDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val input = ctx.contentResolver.openInputStream(uri)
            if (input != null) {
                BufferedInputStream(input).use { buf ->
                    buf.mark(4)
                    val magic = ByteArray(2)
                    val read = buf.read(magic)
                    buf.reset()
                    if (read == 2 && magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte()) {
                        importZip(uri, null)
                    } else {
                        runCatching {
                            val text = buf.readBytes().toString(Charsets.UTF_8)
                            if (text.trimStart().startsWith("{")) {
                                vm.onImportJsonDone(vm.importJsonString(text))
                            } else {
                                vm.onImportCsvDone(vm.importGameSorterCsv(text))
                            }
                        }.onFailure { vm.onImportFailed(it) }
                    }
                }
            }
        }
    }
    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val kind = pendingExport
        scope.launch {
            runCatching {
                val out = ctx.contentResolver.openOutputStream(uri) ?: return@runCatching
                when (kind) {
                    "zip" -> {
                        val pwd = exportPassword.takeIf { it.isNotEmpty() }
                        out.use { vm.onExportZipDone(vm.exportZipTo(it, pwd), pwd != null) }
                    }
                    "games" -> out.bufferedWriter().use { it.write(vm.exportGamesCsvString()) }
                    "shelves" -> out.bufferedWriter().use { it.write(vm.exportShelvesCsvString()) }
                    else -> out.bufferedWriter().use { it.write(vm.exportJsonString()) }
                }
            }.onFailure { vm.onImportFailed(it) }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("更多", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snacks) },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            item {
                SectionCard(title = "导入导出") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                exportPassword = ""
                                exportPassword2 = ""
                                exportPasswordError = ""
                                showExportDialog = true
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("导出备份ZIP") }
                        Button(
                            onClick = { pendingExport = "json"; createDoc.launch("nashi-backup.json") },
                            modifier = Modifier.weight(1f),
                        ) { Text("仅导出JSON") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { pendingExport = "games"; createDoc.launch("games.csv") },
                            modifier = Modifier.weight(1f),
                        ) { Text("导出GS物品") }
                        OutlinedButton(
                            onClick = { pendingExport = "shelves"; createDoc.launch("shelves.csv") },
                            modifier = Modifier.weight(1f),
                        ) { Text("导出GS货架") }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { readDoc.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("导入JSON/CSV/备份ZIP") }
                }
            }
            item {
                SectionCard(title = "多设备同步") {
                    Text(
                        if (sync.bound) {
                            "已绑定 ${sync.serverUrl} · 家庭 ${sync.familyId.take(8)}…" +
                                if (sync.isAdmin) " (管理员)" else ""
                        } else {
                            "未绑定服务端: 先初始化家庭, 或用邀请码加入"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text("服务器地址, 如 http://nas:8080") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { vm.initFamily(serverUrl) },
                            modifier = Modifier.weight(1f),
                        ) { Text("初始化家庭") }
                        Button(
                            onClick = { vm.syncNow() },
                            enabled = sync.bound,
                            modifier = Modifier.weight(1f),
                        ) { Text("立即同步") }
                    }
                    OutlinedTextField(
                        value = inviteCode,
                        onValueChange = { inviteCode = it },
                        label = { Text("邀请码, 如 INV-XXXXXXXX") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { vm.createInvite() },
                            enabled = sync.isAdmin,
                            modifier = Modifier.weight(1f),
                        ) { Text("生成邀请码") }
                        OutlinedButton(
                            onClick = { vm.joinFamily(serverUrl, inviteCode) },
                            enabled = inviteCode.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        ) { Text("加入家庭") }
                    }
                    if (sync.isAdmin) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                showMembers = true
                                vm.loadMembers()
                            },
                            modifier = Modifier.fillMaxWidth()
                                .semantics { contentDescription = "设备与邀请码管理" },
                        ) { Text(stringResource(R.string.members_open)) }
                    }
                }
            }
            item {
                SectionCard(title = "自动备份") {
                    Text("每周一次，保留最近 5 份（含照片）", style = MaterialTheme.typography.bodySmall)
                    Text(vm.backupSummary(), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = vm::backupNow) { Text("立即备份") }
                }
            }
            item {
                NashiNavRow(
                    icon = Icons.Default.History,
                    title = "库存流水",
                    subtitle = "入库/出库/盘点的历史记录",
                    onClick = onOpenHistory,
                    modifier = Modifier.semantics { contentDescription = "库存流水" },
                )
            }
            item {
                NashiNavRow(
                    icon = Icons.Default.RestoreFromTrash,
                    title = "回收站",
                    subtitle = "恢复误删的物品，或彻底删除",
                    onClick = onOpenTrash,
                    modifier = Modifier.semantics { contentDescription = "回收站" },
                )
            }
            item {
                ManageRow(
                    title = "分类管理",
                    entries = categories.map { it.id to it.name },
                    onAdd = vm::addCategory,
                    onRename = vm::renameCategory,
                    onDelete = vm::removeCategory,
                )
            }
            item {
                ManageRow(
                    title = "标签管理",
                    entries = tags.map { it.id to it.name },
                    onAdd = vm::addTag,
                    onRename = vm::renameTag,
                    onDelete = vm::removeTag,
                )
            }
            item {
                NashiNavRow(
                    icon = Icons.Default.Tune,
                    title = "分类字段管理",
                    subtitle = "维护每个分类的表单字段(文本/数字/日期/枚举/布尔)",
                    onClick = onOpenFields,
                    modifier = Modifier.semantics { contentDescription = "分类字段管理" },
                )
            }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("品牌列表", style = MaterialTheme.typography.titleMedium)
                        if (brands.isEmpty()) {
                            Text("暂无品牌数据", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            brands.sortedByDescending { it.c }.forEach { r ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(r.k.ifBlank { "未填品牌" }, style = MaterialTheme.typography.bodyMedium)
                                    Text("${r.c} 件", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("导出备份ZIP") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "设置口令后, 备份包里的数据与照片都用 AES-256 加密。口令无法找回, 忘记即无法恢复。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = exportPassword,
                        onValueChange = { exportPassword = it; exportPasswordError = "" },
                        label = { Text("口令(留空则不加密)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (exportPassword.isNotEmpty()) {
                        OutlinedTextField(
                            value = exportPassword2,
                            onValueChange = { exportPassword2 = it; exportPasswordError = "" },
                            label = { Text("再输一次口令") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (exportPasswordError.isNotBlank()) {
                        Text(
                            exportPasswordError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (exportPassword.isNotEmpty() && exportPassword.length < 6) {
                        exportPasswordError = "口令至少 6 位"
                    } else if (exportPassword.isNotEmpty() && exportPassword != exportPassword2) {
                        exportPasswordError = "两次输入的口令不一致"
                    } else {
                        showExportDialog = false
                        pendingExport = "zip"
                        createDoc.launch("nashi-backup.zip")
                    }
                }) { Text("导出") }
            },
            dismissButton = { TextButton(onClick = { showExportDialog = false }) { Text("取消") } },
        )
    }

    val zipUri = pendingZipUri
    if (zipUri != null) {
        AlertDialog(
            onDismissRequest = { pendingZipUri = null },
            title = { Text("备份包已加密") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("请输入导出备份时设置的口令。", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = zipPassword,
                        onValueChange = { zipPassword = it },
                        label = { Text("口令") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingZipUri = null
                    scope.launch { importZip(zipUri, zipPassword) }
                }) { Text("解密导入") }
            },
            dismissButton = { TextButton(onClick = { pendingZipUri = null }) { Text("取消") } },
        )
    }

    if (showMembers) {
        val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
        AlertDialog(
            onDismissRequest = { showMembers = false },
            title = { Text(stringResource(R.string.members_title)) },
            text = {
                Column(
                    Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(stringResource(R.string.members_devices), style = MaterialTheme.typography.titleSmall)
                    val devices = members?.devices.orEmpty()
                    if (devices.isEmpty()) {
                        Text(stringResource(R.string.members_empty), style = MaterialTheme.typography.bodySmall)
                    } else {
                        devices.forEach { d ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val short = d.deviceId.take(8)
                                Text(
                                    if (d.deviceId == sync.deviceId) {
                                        stringResource(R.string.members_this_device, short)
                                    } else {
                                        stringResource(R.string.members_device_short, short)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    onClick = { vm.revokeDevice(d.deviceId) },
                                    modifier = Modifier.semantics {
                                        contentDescription = "踢出设备 $short"
                                    },
                                ) { Text(stringResource(R.string.members_kick)) }
                            }
                        }
                    }
                    Text(stringResource(R.string.members_invites), style = MaterialTheme.typography.titleSmall)
                    val invites = members?.invites.orEmpty()
                    if (invites.isEmpty()) {
                        Text(stringResource(R.string.members_empty), style = MaterialTheme.typography.bodySmall)
                    } else {
                        invites.forEach { inv ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(inv.code, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        stringResource(
                                            R.string.members_expires,
                                            fmt.format(Date(inv.expiresAt)),
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                                TextButton(
                                    onClick = { vm.revokeInvite(inv.code) },
                                    modifier = Modifier.semantics {
                                        contentDescription = "撤销邀请码 ${inv.code}"
                                    },
                                ) { Text(stringResource(R.string.members_revoke)) }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMembers = false }) {
                    Text(stringResource(R.string.members_close))
                }
            },
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    ElevatedCard(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

@Composable
private fun ManageRow(
    title: String,
    entries: List<Pair<String, String>>,
    onAdd: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    ElevatedCard(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "新增$title") }
            }
            if (entries.isEmpty()) {
                Text("暂无, 点右上角 + 新增", style = MaterialTheme.typography.bodySmall)
            } else {
                entries.forEachIndexed { index, (id, name) ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { editTarget = id to name }) { Icon(Icons.Default.Edit, "重命名 $name") }
                        IconButton(onClick = { onDelete(id) }) { Icon(Icons.Default.Delete, "删除 $name") }
                    }
                }
            }
        }
    }
    if (showAdd) {
        NameDialog(title = "新增", initial = "", onDismiss = { showAdd = false }) { onAdd(it); showAdd = false }
    }
    editTarget?.let { (id, name) ->
        NameDialog(title = "重命名", initial = name, onDismiss = { editTarget = null }) {
            onRename(id, it); editTarget = null
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
