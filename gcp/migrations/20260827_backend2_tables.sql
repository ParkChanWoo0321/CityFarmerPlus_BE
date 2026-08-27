-- CityFarmerPlus backend-2 production schema migration.
-- Apply once to the external MySQL database after taking a backup and before
-- deploying the backend-2 application with JPA_DDL_AUTO=validate.

-- Freeze the course requirement that each education submission was created
-- against. Historical source values are unavailable, so existing rows are
-- backfilled from the course value present when this migration is applied.
SET @required_hours_snapshot_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'education_certificate_submissions'
      AND column_name = 'required_hours_snapshot'
);
SET @add_required_hours_snapshot_sql = IF(
    @required_hours_snapshot_exists = 0,
    'ALTER TABLE education_certificate_submissions ADD COLUMN required_hours_snapshot INT NULL AFTER course_title_snapshot',
    'SELECT 1'
);
PREPARE add_required_hours_snapshot_stmt FROM @add_required_hours_snapshot_sql;
EXECUTE add_required_hours_snapshot_stmt;
DEALLOCATE PREPARE add_required_hours_snapshot_stmt;

UPDATE education_certificate_submissions submission
JOIN education_courses course
  ON course.id = submission.education_course_id
SET submission.required_hours_snapshot = course.required_hours
WHERE submission.required_hours_snapshot IS NULL;

ALTER TABLE education_certificate_submissions
    MODIFY required_hours_snapshot INT NOT NULL;

CREATE TABLE IF NOT EXISTS proxy_registration_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    admin_user_id BIGINT NOT NULL,
    target_user_id BIGINT NOT NULL,
    action_type VARCHAR(64) NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_object_id BIGINT NOT NULL,
    reason VARCHAR(1000) NULL,
    processed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_proxy_registration_logs_target_user (target_user_id, processed_at),
    CONSTRAINT fk_proxy_registration_logs_admin
        FOREIGN KEY (admin_user_id) REFERENCES users (id),
    CONSTRAINT fk_proxy_registration_logs_target
        FOREIGN KEY (target_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Normalize the column when an earlier draft of this migration created it
-- with VARCHAR(30). The longest ActionType currently requires 36 characters.
ALTER TABLE proxy_registration_logs
    MODIFY action_type VARCHAR(64) NOT NULL;

CREATE TABLE IF NOT EXISTS work_assignment_corrections (
    id BIGINT NOT NULL AUTO_INCREMENT,
    work_assignment_id BIGINT NOT NULL,
    previous_work_status VARCHAR(20) NOT NULL,
    new_work_status VARCHAR(20) NOT NULL,
    previous_attendance_status VARCHAR(20) NOT NULL,
    new_attendance_status VARCHAR(20) NOT NULL,
    corrected_by_user_id BIGINT NOT NULL,
    reason VARCHAR(1000) NULL,
    corrected_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_work_assignment_corrections_assignment (work_assignment_id, corrected_at),
    CONSTRAINT fk_work_assignment_corrections_assignment
        FOREIGN KEY (work_assignment_id) REFERENCES work_assignments (id),
    CONSTRAINT fk_work_assignment_corrections_admin
        FOREIGN KEY (corrected_by_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SELECT table_name, column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN (
      'education_certificate_submissions',
      'proxy_registration_logs',
      'work_assignment_corrections'
  )
ORDER BY table_name, ordinal_position;

SELECT table_name, index_name, column_name, seq_in_index
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN ('proxy_registration_logs', 'work_assignment_corrections')
ORDER BY table_name, index_name, seq_in_index;

SELECT table_name, constraint_name, referenced_table_name
FROM information_schema.referential_constraints
WHERE constraint_schema = DATABASE()
  AND table_name IN ('proxy_registration_logs', 'work_assignment_corrections')
ORDER BY table_name, constraint_name;
