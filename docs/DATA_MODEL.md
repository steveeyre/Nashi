# 数据模型

- Category(id, name, icon, createdAt)
- CategoryFieldDef(categoryId, key, label, type[text/number/date/enum/bool], required, optionsCsv)
- Item(id uuid, name, categoryId, brand, quantity, unit, barcode, qrPayload, photoPathsCsv, roomId, spaceId, x,y,z cm, inTime auto now, expiryDate?, minStock, updatedAt, updatedBy, version, isDeleted)
- ItemFieldValue(itemId, key, value)
- Tag(id, name), ItemTag(itemId, tagId)
- HomeRoom(id, name, width,height,depth cm)
- StorageSpace(id, roomId, name, type, width,depth, shelfHeight, columns, maxOverhang, allowOverhang, locked, positionIndex)
- StockTransaction(id uuid, itemId, delta, kind[in/out/move/check], createdAt, deviceId)
- 统计不建表，DAO 里 GROUP BY room/space/category/tag/brand 直接算。

GameSorter 兼容：导出 xlsx 双表 games(Name,W,H,D,ThisSideUp,LockInPlace,Bookcase,Column,Shelf,X,Y,Z) / shelves(Bookcase,Columns,Width,Depth,MaxOverhang,ShelfName,Height,AllowOverhang,Locked)。

## 更多页 IO（已落地，对应 `app/.../core/io/NashiIo.kt`，全文件 344 行）

- JSON 全量备份：`BackupV1(version=1, categories/items/rooms/spaces/tags/itemTags/transactions` 八段）；
  `exportJson(dao)` 用 kotlinx-serialization（复用已有 1.7.3，无新依赖）序列化，
  `importJson(dao, text)` 按 REPLACE 语义回写并返回写入总行数（幂等，可重复导入）。
- GameSorter 双表 CSV（纯字符串拼接；`splitCsvLine/parseCsvTable` 做 RFC4180 子集解析，
  表头识别沿用 `parseGameSorterCsv(header)` 原判定：含 `Bookcase` 即 shelves，否则 games）：
  - games 表头 `Name,Width,Height,Depth,ThisSideUp,LockInPlace,Bookcase,Column,Shelf,X,Y,Z`；
    导出时 Bookcase/Shelf 取空间名（找不到回退 id），仅导出未删除物品；
    导入按 Bookcase 空间名匹配、找不到则 roomId/spaceId 留空，每行新建 Item（默认分类 `cat-other`），追加语义非幂等。
  - shelves 表头 `Bookcase,Columns,Width,Depth,MaxOverhang,ShelfName,Height,AllowOverhang,Locked`；
    导入按空间名匹配更新尺寸（幂等），无匹配则挂首个房间、无房间则建 `room-import`（名“导入”）后新建。
- 入口：`feature/more/MoreScreen.kt`（SAF：`OpenDocument` 读 + `CreateDocument` 写，
  读入后 `trimStart().startsWith("{")` 区分 JSON/CSV，Snackbar 回显行数）+
  `feature/more/MoreViewModel.kt`（Hilt，tags/categories/brands/status 四个 StateFlow）；
  `NavRoot.kt` 第 17/70 行 `more` 路由已由占位 `PlaceholderTab` 换成 `MoreScreen()`。
- 未做：XLSX 导出；物品编辑页的标签绑定 UI；真机 SAF 读写未验证（仅过 `assembleDebug` 编译）。

## 分类 / 标签管理（已落地，对应 `NashiDao` + `feature/more`）

- DAO（`app/.../core/data/NashiDatabase.kt` 第 53-75 行）：
  `tagsFlow/allTags/upsertTags/deleteTag`（软删除 `UPDATE isDeleted=1`，保留同步墓碑）、
  `upsertItemTags/deleteItemTagsOf/allItemTags`、
  `categoriesFlow/deleteCategory`（软删除；`upsertCategories` 已有，未动）、
  导入导出全量读取 `allItems/allSpaces/allTransactions`。
- UI：More 页分类管理行 / 标签管理行（新增、改名、删除对话框），品牌列表复用 `statByBrand` 按件数倒序。

## 标签统计真数据（已落地）

- `NashiDao.statByTag()`（`NashiDatabase.kt` 第 54-55 行）：
  `SELECT t.name AS k, COUNT(*) AS c, SUM(i.quantity) AS q FROM itemTags it JOIN tags t ON t.id=it.tagId JOIN items i ON i.id=it.itemId WHERE i.isDeleted=0 AND t.isDeleted=0 GROUP BY t.id, t.name`
- `StatsViewModel` 追加 `byTag`，`StatsScreen.kt` 第 56/102-104 行标签页由 `TagPlaceholder()` 换成
  `StatPage(rows = byTag, nameOf = { k -> k.ifBlank { "未命名标签" } })`，占位函数已删；真机柱状图渲染未验证。

## 未做事项（文档收口时点）

- 空间 3D（`feature/space` SceneView 预览）仍在施工中。
- 真机验证未做：More 页导入导出往返、分类/标签增删改实时刷新、Stats 标签页渲染均只过编译，未在设备上执行。
- 同步服务端 Docker 构建验证、Postgres 真实切换未做（见 SYNC_PROTOCOL.md）。
