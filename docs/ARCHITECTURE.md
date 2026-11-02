# 架构 — 纳什 Nashi

## 原则

Local-first：Room 是唯一可信源，断网全功能可用，有网后台同步。
Self-hosted：同步服由用户自己 `docker compose up`，无公有云依赖。

```
UI (Compose Screen + ViewModel)
  -> Domain (UseCase: Checkout, Optimize, Remind)
    -> Data (Room DAO + SyncRepository + IoRepository)
      -> Local: Room / DataStore / Files
      -> Remote: Ktor Sync API (pull/push) via WorkManager
```

## 模块划分

- `core/model` 数据类 + Room Entity
- `core/data` DAO, Repository, 同步元数据
- `core/sync` 拉推、冲突解决、DeviceId、Token
- `core/io` JSON 备份、CSV/XLSX (兼容 GameSorter games/shelves 双表)
- `core/optimizer` GameSorter 移植：Box/Shelf/Bookcase + AutoSort
- `core/remind` Expiry/LowStock Worker
- `feature/item` 动态表单 (CategoryFieldDef 驱动) + CameraX 拍照
- `feature/location` 房间→空间→坐标 选择器
- `feature/search` FTS 搜索 + 扫码直达
- `feature/space` 2D Canvas 编辑 + SceneView 3D 预览（3D 仍在施工中）
- `feature/scan` 条码/二维码/小票 OCR
- `feature/stats` 分房间/空间/类别/标签/品牌聚合 + Vico 图（标签页已由占位换成 `statByTag` 真数据，见 DATA_MODEL.md）
- `feature/more` 更多页（已落地）：导入导出（JSON 全量备份 + GameSorter 双表 CSV）+ 分类/标签管理 + 品牌列表，对应 `core/io/NashiIo.kt`（344 行）+ `feature/more/MoreViewModel.kt`（148 行）+ `feature/more/MoreScreen.kt`（242 行），`NavRoot` 的 `more` 路由已接入
- `feature/manage` 分类/标签/品牌 CRUD
- `sync-server` Ktor + Postgres，表结构与客户端对齐（独立 Gradle 工程，自带 `settings.gradle.kts` 不挂入根工程；当前为内存实现，`nextSince` 取 `maxOf(next, now)` 已修复；6 接口本地 curl 联调通过，详见 SYNC_PROTOCOL.md）

## 关键决策

1. 主键全用 UUID 字符串，方便多端合并。
2. 每表带 `updatedAt, updatedBy, isDeleted, version`，同步用 `since` 增量拉取。
3. 库存数量不直接 LWW，用 `StockTransaction` 差值日志合并，避免两端同时出入库丢数。
4. 其余字段 LWW (updatedAt 大者胜)，删除为软删除墓碑。
5. 图片存 App 私有文件，同步只传元数据+可选 WebDAV/S3 镜像，首版先只同步文本+缩略图 URL，照片原图局域网手动同步（后续做分块上传）。
6. 3D 首版只做 Box 建模 (房间/柜/盒均为长方体 + 位姿)，不用网格扫描，够用且可同步。
7. 空间优化超时 12s，与 GameSorter 一致，`withTimeout` 保证移动端不卡。
