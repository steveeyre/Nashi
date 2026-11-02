package com.nashi.feature.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nashi.R
import com.nashi.core.backup.BackupStore
import com.nashi.core.data.ItemRepository
import com.nashi.core.data.NashiDao
import com.nashi.core.data.StatRow
import com.nashi.core.data.StatsAggregator
import com.nashi.core.io.NashiIo
import com.nashi.core.model.Category
import com.nashi.core.model.Tag
import com.nashi.core.sync.MembersResp
import com.nashi.core.sync.SyncRepository
import com.nashi.core.sync.SyncResult
import com.nashi.core.sync.SyncSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 基础功能6(导入导出)+11(分类标签管理): 更多页状态.
 * tags/categories 走 Flow 实时刷新; brands 为一次性聚合, 手动 refreshBrands().
 */
@HiltViewModel
class MoreViewModel @Inject constructor(
    private val dao: NashiDao,
    @Suppress("UNUSED_PARAMETER") repo: ItemRepository,
    private val sync: SyncRepository,
    @ApplicationContext private val ctx: android.content.Context,
) : ViewModel() {

    val tags: StateFlow<List<Tag>> =
        dao.tagsFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<Category>> =
        dao.categoriesFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncSettings: StateFlow<SyncSettings> =
        sync.settingsFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncSettings())

    private val _brands = MutableStateFlow<List<StatRow>>(emptyList())
    val brands: StateFlow<List<StatRow>> = _brands

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    init {
        refreshBrands()
    }

    fun refreshBrands() {
        viewModelScope.launch {
            runCatching { StatsAggregator.statByBrand(dao.allItems()) }.onSuccess { _brands.value = it }
        }
    }

    fun clearStatus() {
        _status.value = ""
    }

    // ---------- 标签 CRUD ----------

    fun addTag(name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                dao.upsertTags(listOf(Tag(UUID.randomUUID().toString(), n)))
            }.onFailure { _status.value = ctx.getString(R.string.status_tag_add_failed, it.message ?: "") }
        }
    }

    fun renameTag(id: String, name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                val old = dao.allTags().firstOrNull { it.id == id } ?: return@runCatching
                dao.upsertTags(listOf(old.copy(name = n, updatedAt = System.currentTimeMillis())))
            }.onFailure { _status.value = ctx.getString(R.string.status_tag_rename_failed, it.message ?: "") }
        }
    }

    fun removeTag(id: String) {
        viewModelScope.launch {
            runCatching {
                dao.deleteTag(id, System.currentTimeMillis())
            }.onFailure { _status.value = ctx.getString(R.string.status_tag_delete_failed, it.message ?: "") }
        }
    }

    // ---------- 分类 CRUD ----------

    fun addCategory(name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                dao.upsertCategories(listOf(Category(UUID.randomUUID().toString(), n)))
            }.onFailure { _status.value = ctx.getString(R.string.status_category_add_failed, it.message ?: "") }
        }
    }

    fun renameCategory(id: String, name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                val old = dao.allCategories().firstOrNull { it.id == id } ?: return@runCatching
                dao.upsertCategories(listOf(old.copy(name = n, updatedAt = System.currentTimeMillis())))
            }.onFailure { _status.value = ctx.getString(R.string.status_category_rename_failed, it.message ?: "") }
        }
    }

    fun removeCategory(id: String) {
        viewModelScope.launch {
            runCatching {
                dao.deleteCategory(id, System.currentTimeMillis())
            }.onFailure { _status.value = ctx.getString(R.string.status_category_delete_failed, it.message ?: "") }
        }
    }

    // ---------- 导入导出 ----------

    // ---------- 自动备份 ----------

    fun backupNow() {
        viewModelScope.launch {
            _status.value = ctx.getString(R.string.status_backup_running)
            runCatching { BackupStore.run(ctx) }
                .onSuccess { _status.value = ctx.getString(R.string.status_backup_done, it.name) }
                .onFailure {
                    _status.value = ctx.getString(
                        R.string.status_backup_failed,
                        it.message ?: ctx.getString(R.string.error_unknown),
                    )
                }
        }
    }

    fun backupSummary(): String {
        val latest = BackupStore.latest(ctx)
        val where = BackupStore.dir(ctx).absolutePath
        val head = latest?.let { ctx.getString(R.string.status_backup_last, it.name) }
            ?: ctx.getString(R.string.status_backup_none)
        return ctx.getString(R.string.status_backup_position, head, where)
    }

    private val photoDir: File get() = File(ctx.filesDir, "photos")

    suspend fun exportZipTo(out: OutputStream, password: String? = null): Int =
        NashiIo.exportZip(dao, photoDir, out, password)

    suspend fun importZipStream(input: InputStream, password: String? = null): Int =
        NashiIo.importZip(dao, photoDir, input, password)

    suspend fun exportJsonString(): String = NashiIo.exportJson(dao)

    suspend fun importJsonString(text: String): Int = NashiIo.importJson(dao, text)

    suspend fun exportGamesCsvString(): String = NashiIo.exportGameSorterCsv(dao)

    suspend fun exportShelvesCsvString(): String = NashiIo.exportGameSorterShelvesCsv(dao)

    /** CSV 文本自动识别 games/shelves 后回写, 返回导入行数. */
    suspend fun importGameSorterCsv(text: String): Int {
        val (header, rows) = NashiIo.parseCsvTable(text)
        if (header.isEmpty()) return 0
        return NashiIo.importGameSorter(dao, NashiIo.parseGameSorterCsv(header), rows)
    }

    /** n 为 importJson 返回的**真实新增行数**(已存在的行会被覆盖但不计入), 故文案用“新增”。 */
    fun onImportJsonDone(n: Int) {
        _status.value = if (n > 0) {
            ctx.getString(R.string.status_import_json_added, n)
        } else {
            ctx.getString(R.string.status_import_json_none)
        }
        refreshBrands()
    }

    fun onImportZipDone(n: Int) {
        _status.value = if (n > 0) {
            ctx.getString(R.string.status_import_zip_added, n)
        } else {
            ctx.getString(R.string.status_import_zip_none)
        }
        refreshBrands()
    }

    fun onExportZipDone(n: Int, encrypted: Boolean = false) {
        _status.value = if (encrypted) {
            ctx.getString(R.string.status_export_zip_encrypted, n)
        } else {
            ctx.getString(R.string.status_export_zip_plain, n)
        }
    }

    fun onImportCsvDone(n: Int) {
        _status.value = ctx.getString(R.string.status_import_csv, n)
        refreshBrands()
    }

    fun onImportFailed(e: Throwable) {
        _status.value = ctx.getString(R.string.status_import_failed, e.message ?: "")
    }

    // ---------- 多设备同步 ----------

    fun initFamily(serverUrl: String) {
        viewModelScope.launch {
            runCatching { sync.initFamily(serverUrl) }
                .onSuccess { _status.value = ctx.getString(R.string.status_family_created, it.familyId.take(8)) }
                .onFailure { _status.value = ctx.getString(R.string.status_family_init_failed, it.message ?: "") }
        }
    }

    fun createInvite() {
        viewModelScope.launch {
            runCatching { sync.createInvite() }
                .onSuccess { _status.value = ctx.getString(R.string.status_invite_created, it) }
                .onFailure { _status.value = ctx.getString(R.string.status_invite_failed, it.message ?: "") }
        }
    }

    fun joinFamily(serverUrl: String, inviteCode: String) {
        viewModelScope.launch {
            runCatching { sync.joinFamily(serverUrl, inviteCode) }
                .onSuccess { _status.value = ctx.getString(R.string.status_joined_family, it.familyId.take(8)) }
                .onFailure { _status.value = ctx.getString(R.string.status_join_failed, it.message ?: "") }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            when (val r = sync.syncNow()) {
                is SyncResult.Success -> _status.value =
                    ctx.getString(R.string.status_sync_done, r.pushed, r.pulled, r.conflicts)
                is SyncResult.Skipped -> _status.value = ctx.getString(R.string.status_sync_skipped, r.reason)
                is SyncResult.Failed -> _status.value = ctx.getString(R.string.status_sync_failed, r.error)
            }
        }
    }

    // ---------- 设备与邀请码管理(仅管理员) ----------

    private val _members = MutableStateFlow<MembersResp?>(null)
    val members: StateFlow<MembersResp?> = _members

    fun loadMembers() {
        viewModelScope.launch {
            runCatching { sync.members() }
                .onSuccess { _members.value = it }
                .onFailure {
                    // require 抛出的"不是管理员"等提示已经是给用户看的文案, 直接展示
                    _status.value = if (it is IllegalArgumentException) {
                        it.message ?: ""
                    } else {
                        ctx.getString(R.string.status_members_failed, it.message ?: "")
                    }
                }
        }
    }

    fun revokeInvite(code: String) {
        viewModelScope.launch {
            runCatching { sync.revokeInvite(code) }
                .onSuccess {
                    _status.value = ctx.getString(R.string.status_invite_revoked)
                    loadMembers()
                }
                .onFailure {
                    _status.value = ctx.getString(R.string.status_invite_revoke_failed, it.message ?: "")
                }
        }
    }

    fun revokeDevice(deviceId: String) {
        viewModelScope.launch {
            runCatching { sync.revokeDevice(deviceId) }
                .onSuccess {
                    _status.value = ctx.getString(R.string.status_device_kicked)
                    loadMembers()
                }
                .onFailure {
                    _status.value = ctx.getString(R.string.status_device_kick_failed, it.message ?: "")
                }
        }
    }
}
