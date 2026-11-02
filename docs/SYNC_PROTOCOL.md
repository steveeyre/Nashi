# 同步协议 (self-hosted)

自建服务：`sync-server/`，`docker compose up -d` 后得到 `https://nas:8080`。

## 认证

- 首次在服务端建 family：`POST /family/init -> { familyId, adminToken }`
- 邀请成员：`POST /family/invite -> { inviteCode }`，App 端输入 inviteCode + serverUrl 绑定
- 之后请求头：`Authorization: Bearer <deviceToken>`，`X-Device-Id: <uuid>`

## 数据模型同步字段 (所有同步表共有)

`id(uuid), updatedAt(epochMs), updatedBy(deviceId), version(int), isDeleted(bool)`

同步表：categories, categoryFields, items, fieldValues, tags, itemTags, rooms, spaces, transactions
不同步：本地照片原图 (后续分块)，统计结果 (本地算)。

### 载荷形态

- `items[]` 直接内嵌 `fieldValues: Map<key,value>` 与 `tagIds: List<id>`，作为该物品的**完整集合**随物品 LWW：
  客户端收到被接受的物品后先清本地 fieldValues/itemTags 再写入，删除才能传播。
- `categories[]` / `tags[]` 是带 `updatedAt` 的普通 LWW 行（含 `isDeleted` 墓碑），按增量下发。
- `categoryFields[]` 无时间戳：push 是家庭的完整快照，服务端整表替换；pull 每页全量下发，
  客户端以服务端快照覆盖本地（快照非空时才覆盖，兼容旧服务端）。为免 pull 循环，
  客户端判空时不计 `categoryFields`。

## 接口

- `GET /sync/pull?since=<ms>&limit=500 -> { changes: {items:[], ...}, nextSince }`
- `POST /sync/push { changes: {...} } -> { conflicts: [...] }`
- `GET /health`

## 冲突

- 普通行：服务端按 updatedAt LWW，输者进 conflicts 回给客户端展示（首版自动取新者）。
- transactions 只追加不更新，按 id 去重后求和，保证库存最���一致。
- 客户端逻辑：Room 写入时更新 updatedAt=now；SyncWorker 先 push 本地 dirty，再 pull 远端，循环至 nextSince==now。

## 客户端调度

- 手动下拉 + WorkManager 每 15min / 网络恢复时触发 `SyncWorker`
- DataStore 存 `lastSince, deviceId, serverUrl, token`

## 本轮实现补充（基础功能5 worker）

## 服务端运行方式（sync-server 独立工程）

- `sync-server/` 为独立 Gradle 工程（含自有 `settings.gradle.kts`，不挂入根
  `settings.gradle.kts`，故根 `assembleDebug` 不会编译它）。
- 依赖：Ktor 2.3.12（core/netty/content-negotiation/kotlinx-json）、
  kotlinx-serialization-json 1.7.3、logback-classic 1.4.14；需 JDK 17。
- 本地构建（已验证：Gradle 8.7 + Microsoft JDK 17.0.20）：
  `cd sync-server` 后
  `& C:/Users/ASUS/tools/gradle-8.7/bin/gradle.bat build -x test --console=plain --no-daemon`
  —— 输出 `BUILD SUCCESSFUL`。
- 本地运行（内存存储，无需 Postgres）：
  同目录 `gradle run`（mainClass `com.nashi.sync.ServerKt`），监听 `:8080`。
- 容器运行：根目录 `docker compose up --build nashi-sync`（服务名 `nashi-sync`，
  端口 `8080:8080`；直连 `sync-server/Dockerfile`，multi-stage eclipse-temurin 17，
  用 `installDist` 产物启动 `bin/sync-server`）。注意：当前 `Dockerfile` 构建阶段
  仍引用根工程 wrapper（`./gradlew :sync-server:installDist` 回退 `gradle installDist`），
  且 `docker-compose.yml` 附带 `db`（postgres:16）与 `DATABASE_URL`，但服务端当前
  为纯内存实现，并未读取该环境变量——见下“生产切 Postgres 步骤”。
- 路由一览（`Server.kt#syncModule`）：`GET /health`、`POST /family/init`、
  `POST /family/invite`、`POST /family/join`、`GET /sync/pull?since=&limit=`、
  `POST /sync/push`。

## 联调实测记录（2026-10-12，本地 gradle run + curl，内存存储）

构建：`gradle build -x test` → `BUILD SUCCESSFUL in 19s, 5 actionable tasks`
（修过 1 个编译错：`Server.kt` 缺 `import io.ktor.server.application.install`；
另修 1 个逻辑：pull 空 store 时 `nextSince` 取 `maxOf(next, now)`，
此前分支恒等于 `next`，空 store 会返回 `nextSince=0` 导致客户端 `lastSince` 永不推进）。

| # | 请求 | status | body 摘要 |
|---|------|--------|-----------|
| 1 | `GET /health` | 200 | `{"status":"ok"}` |
| 2 | `POST /family/init {}` | 200 | `{familyId: 37ca…213b7, adminToken: adm_450e…ae0d4}`（每次随机） |
| 3 | `POST /family/invite {familyId,adminToken}` | 200 | `{inviteCode: INV-54D198B2}`；错 token/错 familyId → 401 `bad familyId/adminToken` |
| 4 | `POST /family/join {inviteCode,deviceId:"pixel-9-test"}` | 200 | `{familyId, deviceToken: dev_8558…957d1}`；错 code → 404 `bad inviteCode` |
| 5 | `GET /sync/pull?since=0`（deviceToken） | 200 | 空 store：`changes` 全空，`nextSince == serverTime == 1791814609803`；无 token → 401 |
| 6 | `POST /sync/push {changes:{1 item "Eggs 12", 1 room "Kitchen", 1 space "Fridge", 1 tx consume -2}, deviceId}` | 200 | `{"conflicts":[]}` |
| 7 | `GET /sync/pull?since=0` | 200 | 4 行原样返回（含 quantity 1.0 / delta -2.0），`nextSince == serverTime` |
| 8 | `GET /sync/pull?since=<push 时刻>` | 200 | 空 changes，`nextSince == serverTime`（增量游标正常） |
| 9 | 过期 push（同 id，updatedAt=1） | 200 | 3 条 LWW conflicts（item/room/space，`serverUpdatedAt` 为 push 时刻，`reason: LWW: server newer`）；重复 tx（同 id）被去重忽略，不进 conflicts |

DTO 对照：服务端 DTO 与 `app/.../core/sync/SyncProtocol.kt` 逐字段对齐；
服务端扩展字段（均有默认值，客户端 `Json{ignoreUnknownKeys=true}` 可忽略）：
`PullResp.serverTime`、`ConflictDto.serverUpdatedAt/reason`、`PushBody.deviceId`。

## 生产切 Postgres 步骤（对应 sync-server/schema.sql）

1. 建库：对 `db` 服务（postgres:16，`POSTGRES_DB=nashi`）执行 `sync-server/schema.sql`
  （`families/devices/invites/items/rooms/spaces/transactions/categories/tags/category_fields`
  十一表；items 另含 `field_values`/`tag_ids` 两个 JSONB 列，三个同步索引）。
2. 加依赖：`exposed`（core/dao/jdbc）+ `org.postgresql:postgresql`；
   服务端启动时读 `DATABASE_URL`（compose 已注入 `jdbc:postgresql://db:5432/nashi`，
   另需 `POSTGRES_USER/POSTGRES_PASSWORD` 或拼入 URL）。
3. 替换存储：用 JDBC/Exposed 实现替换 `InMemoryFamilyStore`（每表加 `family_id`
   做多租户隔离），`Registry` 的 families/devices/invites 落库（内存版重启丢失）。
4. 保持语义：push 按 `(family_id,id)` 查现行 `updated_at`，`incoming.updatedAt >=`
   现行才 UPDATE 否则记 conflicts；transactions 用 `INSERT ... ON CONFLICT DO NOTHING`；
   pull 用 `(family_id, updated_at > since ORDER BY updated_at LIMIT n)`，
   `nextSince` 取本页最大 `updated_at`（空页取 `serverTime`，见本次 `nextSince` 修复）。
5. 切后重跑上表 9 步联调，另测重启后 family/token 仍有效。

- 服务端实现：`sync-server/src/main/kotlin/com/nashi/sync/Server.kt`（Ktor + 内存存储，
  ConcurrentHashMap；生产切 Postgres 见 `sync-server/schema.sql` 注释）。
  构建：`cd sync-server && gradle installDist`，或 `docker compose up --build nashi-sync`。
- 服务端相对 `SyncProtocol.kt` 的**扩展字段**（均有默认值，老客户端可忽略）：
  `PullResp.serverTime`（时钟校准用）、`ConflictDto.serverUpdatedAt/reason`、
  `PushBody.deviceId`；新增 `POST /family/join`（inviteCode 换 deviceToken）。
- 客户端：`core/sync/SyncRepository.kt#syncOnce()`（先 push 脏 items/rooms/spaces，
  再 pull 循环至收敛；普通行 LWW 只覆盖协议字段并保留本地独有列；
  transactions 拉取去重追加）；`core/sync/SyncScheduler.kt#schedulePeriodic()`。
- `SyncWorker` 经 Hilt EntryPoint（`SyncWorkerEntryPoint`）调用 `syncOnce()`，
  失败重试 3 次后转 failure。接入点（`NashiApp.onCreate` 内加两行，
  未直接改 `NashiApp`/`MainActivity` 以避冲突）：
  `SyncScheduler.schedulePeriodic(this)`（import com.nashi.core.sync.SyncScheduler）。
- **DAO 缺口已补上（文档收口时核对代码更新）**：`NashiDao` 现已有
  `dirtyTransactions(since)`（`NashiDatabase.kt` 第 48-49 行，
  `SELECT * FROM transactions WHERE createdAt > :since ORDER BY createdAt LIMIT 500`），
  `SyncRepository.syncOnce()` 第 109、115 行 push 已携带 `dirtyTx`，
  拉取去重追加（第 210-216 行）。此前“push 暂不含本地 transactions”的说法已过时。
- **未做事项（文档收口时点）**：空间 3D 仍在施工中；
  真机 App 端 `syncOnce()` 对新 `nextSince` 的联调未做；
  `docker compose up --build nashi-sync` 未跑（无 Docker 环境，`Dockerfile` 根 wrapper 回退链未验证）；
  Postgres 真实切换未做（仅文档化步骤，`db` + `DATABASE_URL` 对当前内存实现无意义）。
