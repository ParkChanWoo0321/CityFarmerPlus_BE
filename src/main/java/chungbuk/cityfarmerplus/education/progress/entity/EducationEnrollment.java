package chungbuk.cityfarmerplus.education.progress.entity;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.education.entity.EducationCourse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "education_enrollments",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_education_enrollment_user_course",
                        columnNames = {"urban_farmer_user_id", "education_course_id"}
                ),
                @UniqueConstraint(
                        name = "uk_education_enrollment_provider_external",
                        columnNames = {"provider", "external_enrollment_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_education_enrollment_user_status",
                        columnList = "urban_farmer_user_id, progress_status"
                ),
                @Index(
                        name = "idx_education_enrollment_course",
                        columnList = "education_course_id"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EducationEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "urban_farmer_user_id", nullable = false, updatable = false)
    private User urbanFarmer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "education_course_id", nullable = false, updatable = false)
    private EducationCourse course;

    @Column(nullable = false, length = 50, updatable = false)
    private String provider;

    @Column(name = "external_enrollment_id", nullable = false, length = 100, updatable = false)
    private String externalEnrollmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "progress_status", nullable = false, length = 20)
    private ProgressStatus progressStatus;

    @Column(name = "total_minutes", nullable = false)
    private int totalMinutes;

    @Column(name = "completed_minutes", nullable = false)
    private int completedMinutes;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "provider_updated_at", nullable = false)
    private Instant providerUpdatedAt;

    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static EducationEnrollment create(
            User urbanFarmer,
            EducationCourse course,
            String provider,
            String externalEnrollmentId,
            int totalMinutes,
            int completedMinutes,
            Instant occurredAt,
            Instant receivedAt
    ) {
        validateProgress(totalMinutes, completedMinutes);
        EducationEnrollment enrollment = new EducationEnrollment();
        enrollment.urbanFarmer = urbanFarmer;
        enrollment.course = course;
        enrollment.provider = provider;
        enrollment.externalEnrollmentId = externalEnrollmentId;
        enrollment.totalMinutes = totalMinutes;
        enrollment.completedMinutes = completedMinutes;
        enrollment.progressStatus = ProgressStatus.from(totalMinutes, completedMinutes);
        enrollment.startedAt = completedMinutes == 0 ? null : occurredAt;
        enrollment.completedAt = enrollment.progressStatus == ProgressStatus.COMPLETED
                ? occurredAt
                : null;
        enrollment.providerUpdatedAt = occurredAt;
        enrollment.lastSyncedAt = receivedAt;
        return enrollment;
    }

    public boolean applyProgress(
            int totalMinutes,
            int completedMinutes,
            Instant occurredAt,
            Instant receivedAt
    ) {
        validateProgress(totalMinutes, completedMinutes);
        lastSyncedAt = receivedAt;

        if (occurredAt.isBefore(providerUpdatedAt)) {
            return false;
        }
        if (occurredAt.equals(providerUpdatedAt)) {
            if (this.totalMinutes == totalMinutes
                    && this.completedMinutes == completedMinutes) {
                return false;
            }
            throw new IllegalStateException(
                    "동일한 발생 시각에 서로 다른 교육 진도 값이 전달되었습니다."
            );
        }
        if (completedMinutes < this.completedMinutes) {
            throw new IllegalStateException("교육 진도는 이전 값보다 감소할 수 없습니다.");
        }
        if (progressStatus == ProgressStatus.COMPLETED
                && completedMinutes < totalMinutes) {
            throw new IllegalStateException("완료된 교육은 미완료 상태로 되돌릴 수 없습니다.");
        }

        ProgressStatus nextStatus = ProgressStatus.from(totalMinutes, completedMinutes);
        if (startedAt == null && completedMinutes > 0) {
            startedAt = occurredAt;
        }
        if (completedAt == null && nextStatus == ProgressStatus.COMPLETED) {
            completedAt = occurredAt;
        }
        this.totalMinutes = totalMinutes;
        this.completedMinutes = completedMinutes;
        progressStatus = nextStatus;
        providerUpdatedAt = occurredAt;
        return true;
    }

    public int remainingMinutes() {
        return Math.max(0, totalMinutes - completedMinutes);
    }

    public int progressPercentage() {
        return (int) Math.min(100L, (long) completedMinutes * 100 / totalMinutes);
    }

    private static void validateProgress(int totalMinutes, int completedMinutes) {
        if (totalMinutes < 1) {
            throw new IllegalArgumentException("전체 교육 시간은 1분 이상이어야 합니다.");
        }
        if (completedMinutes < 0 || completedMinutes > totalMinutes) {
            throw new IllegalArgumentException(
                    "수강 시간은 0분 이상이며 전체 교육 시간을 초과할 수 없습니다."
            );
        }
    }

    public enum ProgressStatus {
        NOT_STARTED,
        IN_PROGRESS,
        COMPLETED;

        public static ProgressStatus from(int totalMinutes, int completedMinutes) {
            if (completedMinutes == 0) {
                return NOT_STARTED;
            }
            if (completedMinutes == totalMinutes) {
                return COMPLETED;
            }
            return IN_PROGRESS;
        }
    }
}
