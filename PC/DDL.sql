-- 1) 创建数据库（PostgreSQL）
CREATE DATABASE seckill_db
WITH ENCODING = 'UTF8';

-- 2) 切换到目标库后执行以下语句
-- \c seckill_db

-- =========================
-- 表: seckill_goods
-- =========================
CREATE TABLE IF NOT EXISTS seckill_goods (
goods_id BIGINT PRIMARY KEY,
stock INTEGER NOT NULL
);

-- =========================
-- 表: seckill_order
-- =========================
CREATE TABLE IF NOT EXISTS seckill_order (
id BIGSERIAL PRIMARY KEY,
request_id VARCHAR(64) NOT NULL,
user_id BIGINT NOT NULL,
goods_id BIGINT NOT NULL,
status VARCHAR(32) NOT NULL,
created_at TIMESTAMP NOT NULL,
CONSTRAINT uk_order_request_id UNIQUE (request_id),
CONSTRAINT uk_order_user_goods UNIQUE (user_id, goods_id)
);

-- 可选：如果你希望按 goods_id 查询订单更快，可以加这个索引
CREATE INDEX IF NOT EXISTS idx_order_goods_id ON seckill_order(goods_id);

-- =========================
-- 表: seckill_outbox
-- =========================
CREATE TABLE IF NOT EXISTS seckill_outbox (
id BIGSERIAL PRIMARY KEY,
request_id VARCHAR(64) NOT NULL,
payload TEXT NOT NULL,                  -- @Lob 对应 TEXT
status VARCHAR(16) NOT NULL,
retry_count INTEGER NOT NULL,
next_retry_at TIMESTAMP NOT NULL,
last_error VARCHAR(400),
created_at TIMESTAMP NOT NULL,
updated_at TIMESTAMP NOT NULL,
CONSTRAINT uk_outbox_request_id UNIQUE (request_id)
);

-- 对应仓库查询:
-- findByStatusInAndNextRetryAtLessThanEqualOrderByIdAsc
CREATE INDEX IF NOT EXISTS idx_outbox_status_next_retry_id
ON seckill_outbox(status, next_retry_at, id);
