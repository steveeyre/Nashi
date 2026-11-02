# nashi-sync

自托管同步服务（Ktor + Netty，无数据库依赖）。

- 接口：`POST /family/init`、`POST /family/invite`、`POST /family/join`、`POST /family/revoke`、
  `GET /family/members`、`GET /sync/pull?since=`、`POST /sync/push`、`GET /health`
- 冲突：普通行按 `updatedAt` LWW（服务端较新时回执 conflicts）；
  transactions 按 id 去重只追加；字段定义无时间戳，按家庭整表替换。
- 鉴权：`Authorization: Bearer <adminToken|deviceToken>`。服务端只存 token 的 SHA-256 摘要，
  旧快照里的明文 token 在启动加载时自动升级（`upgradeLegacyTokens`）。
- 邀请码：**一次性**、默认 **24 小时**有效（`INVITE_TTL_HOURS` 可调），过期或被撤销后
  再 `join` 返回 404。管理员可撤销邀请码或踢掉某台设备：

  ```bash
  curl -X POST http://nas:8080/family/revoke \
    -H 'Content-Type: application/json' \
    -d '{"familyId":"…","adminToken":"…","deviceId":"…"}'
  ```

  `deviceId` 是客户端首次同步时生成并长期保存的本机编号；`inviteCode` 二选一或同时传。
- 管理页：`GET /family/members`（仅管理员 token）返回已加入的 `deviceId` 列表与未过期邀请码
  （含 `used`/`revoked` 状态与 `expiresAt`）；客户端「更多 → 设备与邀请码管理」就是调这两个接口。
- 持久化：运行时内存 + 每次变更把整份状态原子写到 `DATA_DIR/state.json`，
  启动时自动加载。家庭、设备 token 摘要、邀请码（含有效期/已用/已撤销）、全部同步数据
  都能跨重启保留；1.x 的明文 `adminToken`/`devices`/`invites` 快照会被迁移成新格式。
- 水位：`nextSince` 只取本页实际返回记录的最大时间戳，不掺服务器时钟，
  避免慢时钟设备写入的数据被跳过。

## 运行

```bash
docker compose up -d --build        # 根目录的 docker-compose.yml
# 或本地直接跑
cd sync-server && DATA_DIR=./data gradle run
```

环境变量：`DATA_DIR`（默认 `./data`）、`PORT`（默认 `8080`）、
`INVITE_TTL_HOURS`（默认 `24`，最小 1）。

## 部署提示

- 明文 HTTP 适合可信内网；要暴露到公网请放到 HTTPS 反向代理（Caddy / Nginx）后面，
  客户端同步地址填 `https://`。
- `state.json` 已不含明文 token，但仍是全量家庭数据，注意目录权限与异地备份。

## 测试

```bash
cd sync-server && gradle test --offline
```

`src/main/kotlin/com/nashi/sync/SyncRules.kt` 是与路由无关的纯函数（`applyPush` / `buildPullPage` /
`inviteUsable` / `hashToken` / `upgradeLegacyTokens`），测试直接构造 `FamilyStore` 断言 LWW 冲突回执、
事务去重、字段定义整表替换、拉取分页与水位推进、邀请码有效期与一次性、token 摘要与旧快照升级。

## 后续

`schema.sql` 是多租户 Postgres 版的表结构（含 `family_id` 隔离与同步索引），
当前实现尚未接线；换 Postgres 时按该文件建表，把 `Registry` 的快照读写替换为 JDBC 即可，
LWW 与路由逻辑不变。
