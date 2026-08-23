CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    name VARCHAR(30) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    credits INTEGER NOT NULL DEFAULT 3,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    token_version INTEGER NOT NULL DEFAULT 0,
    email_verified_at TIMESTAMPTZ,

    -- 头像二进制保存在 Garage；这里只保存定位对象和校验文件所需的元数据。
    avatar_bucket VARCHAR(63),
    avatar_object_key VARCHAR(500),
    avatar_original_name VARCHAR(255),
    avatar_content_type VARCHAR(100),
    avatar_size BIGINT,
    avatar_sha256 CHAR(64),
    avatar_updated_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_users_name_not_blank CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_users_credits_non_negative CHECK (credits >= 0),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'DISABLED', 'LOCKED')),
    CONSTRAINT ck_users_avatar_size CHECK (avatar_size IS NULL OR avatar_size > 0),
    CONSTRAINT ck_users_avatar_content_type CHECK (
        avatar_content_type IS NULL
        OR avatar_content_type IN ('image/png', 'image/jpeg', 'image/webp')
    ),
    CONSTRAINT ck_users_avatar_sha256 CHECK (
        avatar_sha256 IS NULL OR avatar_sha256 ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_users_avatar_metadata CHECK (
        (avatar_object_key IS NULL
            AND avatar_bucket IS NULL
            AND avatar_original_name IS NULL
            AND avatar_content_type IS NULL
            AND avatar_size IS NULL
            AND avatar_sha256 IS NULL
            AND avatar_updated_at IS NULL)
        OR
        (avatar_object_key IS NOT NULL
            AND avatar_bucket IS NOT NULL
            AND avatar_content_type IS NOT NULL
            AND avatar_size IS NOT NULL
            AND avatar_sha256 IS NOT NULL
            AND avatar_updated_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_email_lower ON users (lower(email));

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_avatar_object
    ON users (avatar_bucket, avatar_object_key)
    WHERE avatar_object_key IS NOT NULL;
