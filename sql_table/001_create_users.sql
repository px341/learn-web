CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    name VARCHAR(30) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    credits INTEGER NOT NULL DEFAULT 3,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    token_version INTEGER NOT NULL DEFAULT 0,
    email_verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_users_name_not_blank CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_users_credits_non_negative CHECK (credits >= 0),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'DISABLED', 'LOCKED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_email_lower ON users (lower(email));
