# 纳什 Nashi — 家庭物品收纳

Kotlin + Jetpack Compose，本地优先（Room）+ 自托管同步（Ktor，服务端以 JSON 快照落盘）。
本文件描述的是**当前代码实际状态**，不是设计愿景；未实现或有取舍的地方写在「已知限制」。

## 技术栈

- App：Kotlin 2.0、Jetpack Compose (Material3)、Navigation-Compose、Hilt、Coroutines/Flow
- 本地：Room 2.6.1（`version = 2`，含 1→2 Migration）、DataStore Preferences、WorkManager
- 采集：CameraX、ML Kit Barcode / 中文 Text Recognition、Coil
- 展示：Vico 柱状图、SceneView (Filament) 3D 空间视图
- 同步：Ktor Client；服务端 `sync-server/` 为 Ktor + Netty，状态落盘到 `DATA_DIR/state.json`（Docker Compose 挂卷）
- 导入导出：kotlinx-serialization JSON、含照片的 ZIP（可选 AES-256-GCM 口令加密）、GameSorter CSV（games / shelves 双表头识别）

## 目录

- `app/` Android 工程（`app/schemas/` 是 Room 导出的结构 JSON，入库以便 diff 迁移差异）
- `sync-server/` 自托管同步服务（独立 Gradle 构建，不在根 `settings.gradle.kts` 内）
- `docs/` 架构、数据模型、同步协议
- `.rebon/` 本地功能缺口审计（开发记录，不随发布包提供）

## 功能与代码位置

| # | 功能 | 实现 |
|---|---|---|
| 1 | 动态字段 + 拍照 | `feature/item/ItemEditScreen.kt`、`feature/fields/`（字段定义增删改）、`core/media/PhotoStore.kt`（压缩/摆正/删除） |
| 2 | 物品位置（房间→空间→物品） | `feature/location/LocationScreens.kt`、`feature/place/PlaceScreen.kt` |
| 3 | 搜索/筛选/排序 + 批量操作 | `core/data/ItemQuery.kt`（关键词/分类/房间/标签/低库存 + 4 种排序）；列表默认渲染 200 条可增量加载；多选可批量删除/移动/改分类 |
| 4 | 房间/空间建模 + 3D | `feature/place/`（房间/空间增删改）、`feature/space/Space3DView.kt` |
| 5 | 多端同步 | `feature/more/MoreScreen.kt`（服务器/邀请码/立即同步/设备与邀请码管理）、`core/sync/SyncRepository.kt`、`sync-server/`（状态落盘 `DATA_DIR/state.json`） |
| 6 | 导入导出 | `core/io/NashiIo.kt`（JSON 全量 9 集合 / 含照片的 ZIP，可设口令加密 / GameSorter CSV） |
| 7 | 扫码 / 小票 / ISBN | `feature/scan/`（逐行勾选/改数量、可选入库分类、同名合并）、`core/scan/ReceiptParser.kt`、`core/lookup/`（ISBN 识别 + Google Books→Open Library 查询，自动填书名/作者/单位） |
| 8 | 保质期提醒 | `core/remind/ReminderScheduler.kt`、`Workers.kt`、`ReminderNotifications.kt` |
| 9 | 分类统计 | `feature/stats/`（房间/空间/类别/品牌/标签 五个维度） |
| 10 | 低库存提醒 | `StatsAggregator.lowStock` + `LowStockWorker` + 汇总通知 |
| 11 | 分类/标签管理 | `feature/more/MoreScreen.kt`、`feature/fields/CategoryFieldsScreen.kt` |
| 12 | 空间优化 | `core/optimizer/SpaceOptimizer.kt`（GameSorter 算法移植，8 轮贪心） |
| 13 | 库存流水 | `feature/history/`（入库/出库/盘点历史，来源 transactions 表；支持按物品过滤） |
| 14 | 物品详情 | `feature/item/ItemDetailScreen.kt`（照片、动态字段、位置/标签、快捷出入库、跳转流水） |
| 15 | 回收站 | `feature/trash/`（软删除物品恢复/彻底删除），入口在「更多」；`core/data/TombstonePolicy.kt` + `MaintenanceWorker` 每周清理超过 90 天的墓碑与孤儿关联行 |
| 16 | 自动备份 | `core/backup/BackupWorker.kt`（每周一次含照片的 ZIP，保留 5 份；「更多」页可立即备份） |

## 界面风格

- 主题：`app/src/main/java/com/nashi/ui/theme/Theme.kt`（苔绿 + 暖纸的 Material3 配色、8/12/16/22/28 圆角阶梯、统一字形）；深色模式随系统切换，`res/values-night/` 同步窗口背景与状态栏。
- 共用组件：`app/src/main/java/com/nashi/ui/NashiUi.kt`（页头、分组卡片、导航行、空状态、首字色块、统计小卡）。
- 页面约定：主 Tab 用 TopAppBar 显示标题与件数；位置/空间页用 `NashiScreenHeader`；列表项统一为「照片或首字 + 标题 + 副标题 + 右侧状态」；空列表给出原因与下一步动作。
- 可点元素一律带 `contentDescription`，列表卡片与详情信息行合并语义节点，兼顾 TalkBack 与 uiautomator 定位。

## 构建与测试

需要 JDK 17 与 Android SDK 35。

```bash
gradlew.bat :app:assembleDebug :app:testDebugUnitTest --offline
gradlew.bat :app:assembleRelease --offline   # R8 + 资源压缩 + ABI 拆分（未签名）
# 离线环境没有缓存 lint-gradle 时，追加 -x lintVitalAnalyzeRelease -x lintVitalRelease
```

单元测试全部是纯 JVM（JUnit4 + MockK），覆盖：统计/临期/低库存、备份导入导出（九张表）与字段定义/字段值往返、
加密备份（口令解密往返、缺口令/错口令不写库）、小票解析、ISBN 归一化与书籍元数据解析、装箱算法、同步协议序列化（含物品字段值/标签、分类/标签/字段定义）、数据库迁移 SQL、
列表筛选排序、详情页字段映射、墓碑过期策略、批量操作、拉取合并（LWW 方向/本地专属列保留/已硬删墓碑抑制）、
Room 导出 schema 的表与索引校验。
服务端用 `cd sync-server && gradle test --offline`（JUnit4）覆盖 LWW 冲突、事务去重、字段定义整表替换、
拉取分页与水位推进、墓碑行下发、邀请码有效期/一次性/撤销、token 摘要与旧快照升级。

发布签名可选：设置 `NASHI_KEYSTORE` / `NASHI_STORE_PASSWORD` / `NASHI_KEY_ALIAS` / `NASHI_KEY_PASSWORD`
环境变量（或同名 `nashi.*` gradle 属性）即自动签名；未设置时产物保持未签名。
`bundleRelease` 生成 `app/build/outputs/bundle/release/app-release.aab`。

## 安全与隐私

- **局域网同步允许明文 HTTP**：`res/xml/network_security_config.xml` 在 `base-config` 放行
  `cleartextTrafficPermitted`。Android 28+ 默认禁止明文，而自建服务器地址是用户在设置里
  自由填写的（`http://192.168.x.x:8080`），无法用 `domain-config` 白名单覆盖，故主动放开。
  代价是同步 token 与家庭数据在同一局域网内可被窃听：不要把服务端口映射到公网；
  有条件时请给服务端加 TLS 反向代理，并填 `https://` 地址。
- **备份加密**（可选）：导出 ZIP 时设置口令，`backup.json` 与每张照片都用
  AES-256-GCM 独立加密（每条目随机 IV），密钥由 PBKDF2-HMAC-SHA256（12 万次迭代）从口令派生，
  salt 与参数写在包首的 `manifest.json`。不设口令则与旧版兼容（明文 `backup.json` + `photos/`）。
  口令不可找回，忘记即无法恢复；导入加密包时会弹框要口令，缺口令或口令错误都不会写数据库。
- **关闭了系统备份**：`allowBackup="false"` + `data_extraction_rules.xml` 排除云备份与换机直传，
  避免全量物品数据与 DataStore 里的同步 token 被系统带走；备份只走应用内导出。
  注意每周自动备份（`BackupWorker`）写的是未加密 ZIP，位于应用私有目录。

## 已知限制

- **搜索是 LIKE**：`name LIKE '%q%' OR brand LIKE '%q%' OR barcode = q`，上限 100 条。
  没有切 FTS4：SQLite 的 unicode61 分词把连续中文当成一个 token（实测
  `MATCH '收纳盒'` 命中不到 `白色收纳盒`），直接替换会丢子串搜索；字符级 FTS 索引需要在
  SQLite 之外维护影子表，写入路径多、容易失步，故暂不引入。
- **无 androidTest / UI 自动化**：当前构建环境离线且未缓存 `compose-ui-test`、`androidx.test.ext`，
  仅保留可离线运行的纯 JVM 测试。同样因为没有 `room-testing`，迁移断言是手写的 SQL 校验而不是
  `MigrationTestHelper`；`app/schemas/` 的导出 JSON 由 `SchemaExportTest` 守住。
  `lintDebug` 与 Baseline Profile 同样跑不了：离线缓存缺 `lint-gradle:31.6.1`、`benchmark-macro-junit4`。
- **静态 UI 文案仍硬编码**：状态/错误提示已迁到 `strings.xml`（`MoreViewModel` 与设备管理对话框），
  页面标题、按钮、列表说明仍是中文字面量；全量 i18n 尚未开始。
- **release 默认未签名**：只有配置 keystore 参数才会签名；R8 混淆产物未做真机冒烟，
  `assembleRelease`/`bundleRelease` 只验证可打包。
- **自动备份的位置**：ZIP 写在 `Android/data/com.nashi/files/backups`，Android 11+ 的文件管理器
  不能直接浏览该目录，需要用 USB 或「手动导出备份ZIP」取走；备份本身每周自动生成并保留 5 份。
- **同步不含照片**：`photoPathsCsv` 是本地文件路径，跨设备无效；照片仍只存在于拍摄设备。
  本机照片拍照后压缩到长边 1600px，编辑页可逐张删除，回收站彻底删除时会一并清理文件。
  物品的品牌/单位/条码/入库时间/保质期/最低库存/尺寸、自定义字段值与标签集合均随物品整体 LWW；
  字段定义无时间戳，按家庭快照整表替换（后写者胜）。
- **同步服务端用 JSON 快照落盘**（`DATA_DIR/state.json`，每次变更原子替换），不是 Postgres：
  `schema.sql` 保留为后续替换 JDBC 后端的表结构；单家庭数据量小，快照足够。
- **拉取水位只按本页实际返回行推进**：不再掺服务器时钟，慢时钟设备推送的数据不会被跳过。
- **回收站的「彻底删除」是本机行为**：硬删后本机会记住这些 id，同步时忽略服务端残留墓碑；
  其他设备仍可能在各自回收站看到该物品，需要各自清理。
- 不支持 xlsx（无 Apache POI 依赖），CSV 为 GameSorter 兼容格式。
- **备份口令不可找回**：加密 ZIP 没有恢复码或后门，忘记口令只能重新导出。

## 许可证

[MIT](LICENSE)
