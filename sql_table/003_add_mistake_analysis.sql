BEGIN;

-- status 保留为记录生命周期（ACTIVE/ARCHIVED）；接口中的分析状态单独保存，
-- 避免归档状态与 queued/analyzing/completed/failed 相互覆盖。
ALTER TABLE personal_questions
    ADD COLUMN IF NOT EXISTS user_answer TEXT,
    ADD COLUMN IF NOT EXISTS analysis_status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    ADD COLUMN IF NOT EXISTS mastered BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS analysis_summary TEXT,
    ADD COLUMN IF NOT EXISTS analysis_knowledge JSONB,
    ADD COLUMN IF NOT EXISTS analysis_steps JSONB,
    ADD COLUMN IF NOT EXISTS analysis_suggestion TEXT,
    ADD COLUMN IF NOT EXISTS analysis_answer TEXT,
    ADD COLUMN IF NOT EXISTS analysis_confidence SMALLINT,
    ADD COLUMN IF NOT EXISTS failure_message TEXT;

COMMENT ON COLUMN personal_questions.status IS
    '记录生命周期：ACTIVE 或 ARCHIVED；不作为 API 的分析状态返回';
COMMENT ON COLUMN personal_questions.analysis_status IS
    '分析任务状态：QUEUED、ANALYZING、COMPLETED 或 FAILED';
COMMENT ON COLUMN personal_questions.image_object_key IS
    'Garage 私有对象键；预签名 URL 及其过期时间不得写入数据库';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_personal_questions_analysis_status'
          AND conrelid = 'personal_questions'::regclass
    ) THEN
        ALTER TABLE personal_questions
            ADD CONSTRAINT ck_personal_questions_analysis_status
            CHECK (analysis_status IN ('QUEUED', 'ANALYZING', 'COMPLETED', 'FAILED'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_personal_questions_analysis_knowledge'
          AND conrelid = 'personal_questions'::regclass
    ) THEN
        ALTER TABLE personal_questions
            ADD CONSTRAINT ck_personal_questions_analysis_knowledge
            CHECK (
                analysis_knowledge IS NULL
                OR jsonb_typeof(analysis_knowledge) = 'array'
            );
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_personal_questions_analysis_steps'
          AND conrelid = 'personal_questions'::regclass
    ) THEN
        ALTER TABLE personal_questions
            ADD CONSTRAINT ck_personal_questions_analysis_steps
            CHECK (
                analysis_steps IS NULL
                OR jsonb_typeof(analysis_steps) = 'array'
            );
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_personal_questions_analysis_confidence'
          AND conrelid = 'personal_questions'::regclass
    ) THEN
        ALTER TABLE personal_questions
            ADD CONSTRAINT ck_personal_questions_analysis_confidence
            CHECK (
                analysis_confidence IS NULL
                OR analysis_confidence BETWEEN 0 AND 100
            );
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_personal_questions_analysis_payload'
          AND conrelid = 'personal_questions'::regclass
    ) THEN
        ALTER TABLE personal_questions
            ADD CONSTRAINT ck_personal_questions_analysis_payload
            CHECK (
                (
                    analysis_status = 'COMPLETED'
                    AND analysis_summary IS NOT NULL
                    AND analysis_knowledge IS NOT NULL
                    AND analysis_steps IS NOT NULL
                    AND analysis_suggestion IS NOT NULL
                    AND analysis_answer IS NOT NULL
                    AND analysis_confidence IS NOT NULL
                    AND failure_message IS NULL
                )
                OR
                (
                    analysis_status = 'FAILED'
                    AND analysis_summary IS NULL
                    AND analysis_knowledge IS NULL
                    AND analysis_steps IS NULL
                    AND analysis_suggestion IS NULL
                    AND analysis_answer IS NULL
                    AND analysis_confidence IS NULL
                    AND failure_message IS NOT NULL
                    AND length(trim(failure_message)) > 0
                )
                OR
                (
                    analysis_status IN ('QUEUED', 'ANALYZING')
                    AND analysis_summary IS NULL
                    AND analysis_knowledge IS NULL
                    AND analysis_steps IS NULL
                    AND analysis_suggestion IS NULL
                    AND analysis_answer IS NULL
                    AND analysis_confidence IS NULL
                    AND failure_message IS NULL
                )
            );
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_personal_questions_mastered'
          AND conrelid = 'personal_questions'::regclass
    ) THEN
        ALTER TABLE personal_questions
            ADD CONSTRAINT ck_personal_questions_mastered
            CHECK (NOT mastered OR analysis_status = 'COMPLETED');
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_personal_questions_user_analysis_created
    ON personal_questions (user_id, analysis_status, created_at DESC)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_personal_questions_user_mastered_created
    ON personal_questions (user_id, mastered, created_at DESC)
    WHERE status = 'ACTIVE';

COMMIT;
