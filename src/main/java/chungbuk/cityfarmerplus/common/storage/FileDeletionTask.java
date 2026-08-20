package chungbuk.cityfarmerplus.common.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "file_deletion_tasks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_file_deletion_tasks_storage_key",
                columnNames = "storage_key"
        ),
        indexes = @Index(
                name = "idx_file_deletion_tasks_ready",
                columnList = "completed_at,next_attempt_at"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FileDeletionTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "storage_key", nullable = false, length = 500, updatable = false)
    private String storageKey;

    @Column(nullable = false, length = 50, updatable = false)
    private String reason;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error_type", length = 200)
    private String lastErrorType;

    @Column(name = "completed_at")
    private Instant completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static FileDeletionTask accountWithdrawal(String storageKey, Instant now) {
        return pending(storageKey, "ACCOUNT_WITHDRAWAL", now);
    }

    public static FileDeletionTask pending(
            String storageKey,
            String reason,
            Instant now
    ) {
        if (reason == null || reason.isBlank() || reason.length() > 50) {
            throw new IllegalArgumentException("파일 삭제 사유가 올바르지 않습니다.");
        }
        FileDeletionTask task = new FileDeletionTask();
        task.storageKey = storageKey;
        task.reason = reason;
        task.attempts = 0;
        task.nextAttemptAt = now;
        return task;
    }

    public void complete(Instant now) {
        completedAt = now;
        lastErrorType = null;
    }

    public void retryAfterFailure(Throwable failure, Instant now) {
        attempts++;
        long exponent = Math.min(attempts - 1L, 7L);
        long delaySeconds = Math.min(3600L, 30L * (1L << exponent));
        nextAttemptAt = now.plusSeconds(delaySeconds);
        lastErrorType = failure.getClass().getName();
    }
}
