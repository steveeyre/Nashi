-- Nashi sync-server 的 Postgres 表结构（多租户版）。
-- 当前实现：内存 + DATA_DIR/state.json 原子快照（见 Server.kt 的 Registry），重启可恢复，
-- 本文件尚未接线，保留给后续 JDBC 替换。切换时：
--   1) 加依赖 pgjdbc / exposed；
--   2) 用下表替换 Registry 的快照读写：每个 *DTO 表加 family_id 列做多租户隔离；
--   3) LWW 语义保持不变：push 时按 (family_id, id) 查现行 updated_at，
--      incoming.updatedAt >= 现行才 UPDATE，否则记 conflicts；
--   4) transactions 主键 (family_id, id)，INSERT ... ON CONFLICT DO NOTHING；
--   5) pull 用 (family_id, updated_at > since ORDER BY updated_at LIMIT n)，
--      nextSince 取本页最大 updated_at（不要掺服务器时钟）。

CREATE TABLE IF NOT EXISTS families (
    family_id  TEXT PRIMARY KEY,
    admin_token TEXT NOT NULL UNIQUE,
    created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS devices (
    device_token TEXT PRIMARY KEY,
    family_id    TEXT NOT NULL REFERENCES families(family_id) ON DELETE CASCADE,
    device_id    TEXT NOT NULL,
    created_at   BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS invites (
    invite_code TEXT PRIMARY KEY,
    family_id   TEXT NOT NULL REFERENCES families(family_id) ON DELETE CASCADE,
    created_at  BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS items (
    family_id   TEXT NOT NULL REFERENCES families(family_id) ON DELETE CASCADE,
    id          TEXT NOT NULL,
    name        TEXT NOT NULL,
    category_id TEXT NOT NULL,
    quantity    DOUBLE PRECISION NOT NULL DEFAULT 1,
    room_id     TEXT NOT NULL DEFAULT '',
    space_id    TEXT NOT NULL DEFAULT '',
    updated_at  BIGINT NOT NULL,
    updated_by  TEXT NOT NULL DEFAULT '',
    version     INT  NOT NULL DEFAULT 1,
    is_deleted  BOOLEAN NOT NULL DEFAULT FALSE,
    brand       TEXT NOT NULL DEFAULT '',
    unit        TEXT NOT NULL DEFAULT '件',
    barcode     TEXT NOT NULL DEFAULT '',
    in_time     BIGINT NOT NULL DEFAULT 0,
    expiry_date BIGINT,
    min_stock   DOUBLE PRECISION NOT NULL DEFAULT 0,
    width       REAL NOT NULL DEFAULT 0,
    height      REAL NOT NULL DEFAULT 0,
    depth       REAL NOT NULL DEFAULT 0,
    field_values JSONB NOT NULL DEFAULT '{}'::jsonb,
    tag_ids     JSONB NOT NULL DEFAULT '[]'::jsonb,
    PRIMARY KEY (family_id, id)
);
CREATE INDEX IF NOT EXISTS idx_items_sync ON items (family_id, updated_at);

CREATE TABLE IF NOT EXISTS rooms (
    family_id  TEXT NOT NULL REFERENCES families(family_id) ON DELETE CASCADE,
    id         TEXT NOT NULL,
    name       TEXT NOT NULL,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (family_id, id)
);
CREATE INDEX IF NOT EXISTS idx_rooms_sync ON rooms (family_id, updated_at);

CREATE TABLE IF NOT EXISTS spaces (
    family_id  TEXT NOT NULL REFERENCES families(family_id) ON DELETE CASCADE,
    id         TEXT NOT NULL,
    room_id    TEXT NOT NULL,
    name       TEXT NOT NULL,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (family_id, id)
);
CREATE INDEX IF NOT EXISTS idx_spaces_sync ON spaces (family_id, updated_at);

CREATE TABLE IF NOT EXISTS transactions (
    family_id  TEXT NOT NULL REFERENCES families(family_id) ON DELETE CASCADE,
    id         TEXT NOT NULL,
    item_id    TEXT NOT NULL,
    delta      DOUBLE PRECISION NOT NULL,
    kind       TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    device_id  TEXT NOT NULL DEFAULT '',
    PRIMARY KEY (family_id, id)
);
CREATE INDEX IF NOT EXISTS idx_tx_sync ON transactions (family_id, created_at);

CREATE TABLE IF NOT EXISTS categories (
    family_id  TEXT NOT NULL REFERENCES families(family_id) ON DELETE CASCADE,
    id         TEXT NOT NULL,
    name       TEXT NOT NULL,
    icon       TEXT NOT NULL DEFAULT '',
    updated_at BIGINT NOT NULL,
    updated_by TEXT NOT NULL DEFAULT '',
    version    INT  NOT NULL DEFAULT 1,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (family_id, id)
);
CREATE INDEX IF NOT EXISTS idx_categories_sync ON categories (family_id, updated_at);

CREATE TABLE IF NOT EXISTS tags (
    family_id  TEXT NOT NULL REFERENCES families(family_id) ON DELETE CASCADE,
    id         TEXT NOT NULL,
    name       TEXT NOT NULL,
    updated_at BIGINT NOT NULL,
    updated_by TEXT NOT NULL DEFAULT '',
    version    INT  NOT NULL DEFAULT 1,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (family_id, id)
);
CREATE INDEX IF NOT EXISTS idx_tags_sync ON tags (family_id, updated_at);

-- 字段定义无时间戳：push 是该家庭的完整快照，按 family_id 整表替换（DELETE + INSERT）。
CREATE TABLE IF NOT EXISTS category_fields (
    family_id   TEXT NOT NULL REFERENCES families(family_id) ON DELETE CASCADE,
    id          TEXT NOT NULL,
    category_id TEXT NOT NULL,
    key         TEXT NOT NULL,
    label       TEXT NOT NULL,
    type        TEXT NOT NULL,
    required    BOOLEAN NOT NULL DEFAULT FALSE,
    options_csv TEXT NOT NULL DEFAULT '',
    PRIMARY KEY (family_id, id)
);
