-- CityFarmerPlus education real-time progress schema migration.
-- Apply once after taking a verified backup and before deploying the application
-- revision with JPA_DDL_AUTO=validate.

CREATE TABLE IF NOT EXISTS education_enrollments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    urban_farmer_user_id BIGINT NOT NULL,
    education_course_id BIGINT NOT NULL,
    provider VARCHAR(50) COLLATE utf8mb4_bin NOT NULL,
    external_enrollment_id VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
    progress_status VARCHAR(20) NOT NULL,
    total_minutes INT NOT NULL,
    completed_minutes INT NOT NULL,
    started_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    provider_updated_at DATETIME(6) NOT NULL,
    last_synced_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_education_enrollment_user_course
        UNIQUE (urban_farmer_user_id, education_course_id),
    CONSTRAINT uk_education_enrollment_provider_external
        UNIQUE (provider, external_enrollment_id),
    INDEX idx_education_enrollment_user_status
        (urban_farmer_user_id, progress_status),
    INDEX idx_education_enrollment_course (education_course_id),
    CONSTRAINT fk_education_enrollment_user
        FOREIGN KEY (urban_farmer_user_id) REFERENCES users (id),
    CONSTRAINT fk_education_enrollment_course
        FOREIGN KEY (education_course_id) REFERENCES education_courses (id),
    CONSTRAINT chk_education_enrollment_total_minutes
        CHECK (total_minutes > 0),
    CONSTRAINT chk_education_enrollment_completed_minutes
        CHECK (completed_minutes >= 0 AND completed_minutes <= total_minutes)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS education_progress_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    education_enrollment_id BIGINT NOT NULL,
    provider VARCHAR(50) COLLATE utf8mb4_bin NOT NULL,
    provider_event_id VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
    payload_sha256 VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    total_minutes INT NOT NULL,
    completed_minutes INT NOT NULL,
    applied BIT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    received_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_education_progress_event_provider_event
        UNIQUE (provider, provider_event_id),
    INDEX idx_education_progress_event_enrollment_time
        (education_enrollment_id, occurred_at),
    INDEX idx_education_progress_event_received (received_at),
    CONSTRAINT fk_education_progress_event_enrollment
        FOREIGN KEY (education_enrollment_id) REFERENCES education_enrollments (id),
    CONSTRAINT chk_education_progress_event_total_minutes
        CHECK (total_minutes > 0),
    CONSTRAINT chk_education_progress_event_completed_minutes
        CHECK (completed_minutes >= 0 AND completed_minutes <= total_minutes)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SELECT table_name, column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN ('education_enrollments', 'education_progress_events')
ORDER BY table_name, ordinal_position;

SELECT table_name, index_name, column_name, seq_in_index
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN ('education_enrollments', 'education_progress_events')
ORDER BY table_name, index_name, seq_in_index;

SELECT table_name, constraint_name, referenced_table_name
FROM information_schema.referential_constraints
WHERE constraint_schema = DATABASE()
  AND table_name IN ('education_enrollments', 'education_progress_events')
ORDER BY table_name, constraint_name;
