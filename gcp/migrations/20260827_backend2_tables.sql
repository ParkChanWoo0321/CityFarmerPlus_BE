-- CityFarmerPlus backend-2 production schema migration.
-- Apply once to the external MySQL database after taking a backup and before
-- deploying the backend-2 application with JPA_DDL_AUTO=validate.

CREATE TABLE IF NOT EXISTS proxy_registration_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    admin_user_id BIGINT NOT NULL,
    target_user_id BIGINT NOT NULL,
    action_type VARCHAR(30) NOT NULL,
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

SELECT table_name
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('proxy_registration_logs', 'work_assignment_corrections')
ORDER BY table_name;
