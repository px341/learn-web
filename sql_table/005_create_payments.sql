BEGIN;

-- 套餐由服务端维护，客户端只提交 plan_id，不能提交可信金额或额度。
CREATE TABLE IF NOT EXISTS payment_plans (
    id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    credits INTEGER NOT NULL,
    price_fen INTEGER NOT NULL,
    description VARCHAR(255),
    recommended BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_payment_plans_id_not_blank
        CHECK (length(trim(id)) > 0),
    CONSTRAINT ck_payment_plans_name_not_blank
        CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_payment_plans_credits_positive
        CHECK (credits > 0),
    CONSTRAINT ck_payment_plans_price_positive
        CHECK (price_fen > 0),
    CONSTRAINT ck_payment_plans_status
        CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_payment_plans_sort_order
        CHECK (sort_order >= 0)
);

-- API.md 中的演示套餐。重复执行迁移时同步服务端配置，但历史订单使用自己的快照。
INSERT INTO payment_plans (
    id, name, credits, price_fen, description, recommended, status, sort_order
) VALUES
    ('trial', '单次体验', 1, 100, '适合先试试看', FALSE, 'ACTIVE', 10),
    ('starter', '进阶学习包', 10, 800, '平均每次 ¥0.8', TRUE, 'ACTIVE', 20)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    credits = EXCLUDED.credits,
    price_fen = EXCLUDED.price_fen,
    description = EXCLUDED.description,
    recommended = EXCLUDED.recommended,
    status = EXCLUDED.status,
    sort_order = EXCLUDED.sort_order,
    updated_at = CURRENT_TIMESTAMP;

-- 每次支付创建一条订单。套餐名称、额度和金额保存快照，避免套餐修改影响历史记录。
CREATE TABLE IF NOT EXISTS payment_orders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    plan_id VARCHAR(50) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,

    plan_name VARCHAR(100) NOT NULL,
    credits_added INTEGER NOT NULL,
    amount_fen INTEGER NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'CNY',

    provider VARCHAR(30) NOT NULL DEFAULT 'MOCK',
    provider_order_id VARCHAR(128),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    failure_code VARCHAR(100),
    failure_message VARCHAR(1000),
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_payment_orders_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_orders_plan
        FOREIGN KEY (plan_id) REFERENCES payment_plans(id) ON DELETE RESTRICT,
    CONSTRAINT uk_payment_orders_user_idempotency
        UNIQUE (user_id, idempotency_key),
    CONSTRAINT ck_payment_orders_idempotency_key_not_blank
        CHECK (length(trim(idempotency_key)) > 0),
    CONSTRAINT ck_payment_orders_plan_name_not_blank
        CHECK (length(trim(plan_name)) > 0),
    CONSTRAINT ck_payment_orders_credits_positive
        CHECK (credits_added > 0),
    CONSTRAINT ck_payment_orders_amount_positive
        CHECK (amount_fen > 0),
    CONSTRAINT ck_payment_orders_currency
        CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_payment_orders_provider_not_blank
        CHECK (length(trim(provider)) > 0),
    CONSTRAINT ck_payment_orders_status
        CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_payment_orders_paid_state
        CHECK (
            (status = 'PAID' AND paid_at IS NOT NULL)
            OR (status <> 'PAID' AND paid_at IS NULL)
        ),
    CONSTRAINT ck_payment_orders_failure_state
        CHECK (
            status = 'FAILED'
            OR (failure_code IS NULL AND failure_message IS NULL)
        )
);

CREATE INDEX IF NOT EXISTS idx_payment_plans_active_sort
    ON payment_plans (sort_order, id)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_payment_orders_user_created
    ON payment_orders (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_payment_orders_status_created
    ON payment_orders (status, created_at);

CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_orders_provider_order
    ON payment_orders (provider, provider_order_id)
    WHERE provider_order_id IS NOT NULL;

COMMENT ON COLUMN payment_orders.idempotency_key IS
    '客户端 Idempotency-Key；同一用户重复请求只能命中同一订单';
COMMENT ON COLUMN payment_orders.amount_fen IS
    '下单时的套餐金额快照，单位为人民币分';
COMMENT ON COLUMN payment_orders.credits_added IS
    '订单成功时原子增加到 users.credits 的额度快照';

COMMIT;
