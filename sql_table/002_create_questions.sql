-- 官方题由内网管理端维护并供所有用户共享。
CREATE TABLE IF NOT EXISTS official_questions (
    id UUID PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    subject VARCHAR(30) NOT NULL,
    chapter VARCHAR(100),
    question_type VARCHAR(30),
    stem_text TEXT,

    -- 当前 Demo 一题只保存一张主图；二进制位于 Garage，这里只保存对象信息。
    image_object_key VARCHAR(500),
    image_original_name VARCHAR(255),
    image_content_type VARCHAR(100),
    image_size BIGINT,
    image_sha256 CHAR(64),

    standard_answer TEXT,
    standard_analysis JSONB NOT NULL DEFAULT '{}'::JSONB,

    source_name VARCHAR(100),
    source_year INTEGER,
    source_question_code VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',

    -- 管理端可以使用企业 SSO subject，无需为此建立本地权限表。
    created_by VARCHAR(100) NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    published_at TIMESTAMPTZ,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_official_questions_title_not_blank
        CHECK (length(trim(title)) > 0),
    CONSTRAINT ck_official_questions_subject_not_blank
        CHECK (length(trim(subject)) > 0),
    CONSTRAINT ck_official_questions_content
        CHECK (stem_text IS NOT NULL OR image_object_key IS NOT NULL),
    CONSTRAINT ck_official_questions_image_size
        CHECK (image_size IS NULL OR image_size > 0),
    CONSTRAINT ck_official_questions_image_metadata
        CHECK (
            (image_object_key IS NULL
                AND image_original_name IS NULL
                AND image_content_type IS NULL
                AND image_size IS NULL
                AND image_sha256 IS NULL)
            OR
            (image_object_key IS NOT NULL
                AND image_content_type IS NOT NULL
                AND image_size IS NOT NULL
                AND image_sha256 IS NOT NULL)
        ),
    CONSTRAINT ck_official_questions_image_sha256
        CHECK (image_sha256 IS NULL OR image_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_official_questions_source_year
        CHECK (source_year IS NULL OR source_year BETWEEN 1900 AND 9999),
    CONSTRAINT ck_official_questions_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT ck_official_questions_publish_state
        CHECK (
            (status = 'PUBLISHED' AND published_at IS NOT NULL)
            OR status <> 'PUBLISHED'
        ),
    CONSTRAINT ck_official_questions_version
        CHECK (version >= 0)
);

CREATE INDEX IF NOT EXISTS idx_official_questions_catalog
    ON official_questions (status, subject, chapter);

CREATE INDEX IF NOT EXISTS idx_official_questions_source
    ON official_questions (source_name, source_year);

-- 个人题完全属于上传用户，可以没有任何对应的官方题。
CREATE TABLE IF NOT EXISTS personal_questions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    matched_official_question_id UUID,

    title VARCHAR(100) NOT NULL,
    subject VARCHAR(30) NOT NULL,
    chapter VARCHAR(100),
    question_type VARCHAR(30),
    stem_text TEXT,

    -- 私有图片使用 users/{userId}/personal-questions/{questionId}/original.{ext}。
    image_object_key VARCHAR(500),
    image_original_name VARCHAR(255),
    image_content_type VARCHAR(100),
    image_size BIGINT,
    image_sha256 CHAR(64),

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_personal_questions_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_personal_questions_official_match
        FOREIGN KEY (matched_official_question_id)
        REFERENCES official_questions(id) ON DELETE SET NULL,
    CONSTRAINT ck_personal_questions_title_not_blank
        CHECK (length(trim(title)) > 0),
    CONSTRAINT ck_personal_questions_subject_not_blank
        CHECK (length(trim(subject)) > 0),
    CONSTRAINT ck_personal_questions_content
        CHECK (stem_text IS NOT NULL OR image_object_key IS NOT NULL),
    CONSTRAINT ck_personal_questions_image_size
        CHECK (image_size IS NULL OR image_size > 0),
    CONSTRAINT ck_personal_questions_image_metadata
        CHECK (
            (image_object_key IS NULL
                AND image_original_name IS NULL
                AND image_content_type IS NULL
                AND image_size IS NULL
                AND image_sha256 IS NULL)
            OR
            (image_object_key IS NOT NULL
                AND image_content_type IS NOT NULL
                AND image_size IS NOT NULL
                AND image_sha256 IS NOT NULL)
        ),
    CONSTRAINT ck_personal_questions_image_sha256
        CHECK (image_sha256 IS NULL OR image_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_personal_questions_status
        CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_personal_questions_version
        CHECK (version >= 0)
);

CREATE INDEX IF NOT EXISTS idx_personal_questions_user_created
    ON personal_questions (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_personal_questions_official_match
    ON personal_questions (matched_official_question_id)
    WHERE matched_official_question_id IS NOT NULL;
