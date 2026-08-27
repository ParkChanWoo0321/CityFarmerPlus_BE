package chungbuk.cityfarmerplus.work.entity;

import chungbuk.cityfarmerplus.auth.entity.User;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "work_assignment_corrections",
        indexes = @Index(
                name = "idx_work_assignment_corrections_assignment",
                columnList = "work_assignment_id,corrected_at"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkAssignmentCorrection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_assignment_id", nullable = false, updatable = false)
    private WorkAssignment workAssignment;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_work_status", nullable = false, length = 20, updatable = false)
    private WorkAssignment.WorkStatus previousWorkStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_work_status", nullable = false, length = 20, updatable = false)
    private WorkAssignment.WorkStatus newWorkStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_attendance_status", nullable = false, length = 20, updatable = false)
    private WorkAssignment.AttendanceStatus previousAttendanceStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_attendance_status", nullable = false, length = 20, updatable = false)
    private WorkAssignment.AttendanceStatus newAttendanceStatus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "corrected_by_user_id", nullable = false, updatable = false)
    private User correctedBy;

    @Column(length = 1000, updatable = false)
    private String reason;

    @CreationTimestamp
    @Column(name = "corrected_at", nullable = false, updatable = false)
    private Instant correctedAt;

    public static WorkAssignmentCorrection record(
            WorkAssignment workAssignment,
            WorkAssignment.WorkStatus previousWorkStatus,
            WorkAssignment.WorkStatus newWorkStatus,
            WorkAssignment.AttendanceStatus previousAttendanceStatus,
            WorkAssignment.AttendanceStatus newAttendanceStatus,
            User correctedBy,
            String reason
    ) {
        if (workAssignment == null) {
            throw new IllegalArgumentException("정정 대상 근무 일정은 필수입니다.");
        }
        if (correctedBy == null
                || correctedBy.getUserType() != User.UserType.CENTER_ADMIN
                || !correctedBy.isActive()) {
            throw new IllegalArgumentException(
                    "활성 상태인 담당자만 출결 정정 이력을 기록할 수 있습니다."
            );
        }
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isBlank()) {
            throw new IllegalArgumentException("정정 사유는 필수입니다.");
        }
        if (normalizedReason.length() > 1000) {
            throw new IllegalArgumentException("정정 사유는 1000자 이하여야 합니다.");
        }
        WorkAssignmentCorrection correction = new WorkAssignmentCorrection();
        correction.workAssignment = workAssignment;
        correction.previousWorkStatus = previousWorkStatus;
        correction.newWorkStatus = newWorkStatus;
        correction.previousAttendanceStatus = previousAttendanceStatus;
        correction.newAttendanceStatus = newAttendanceStatus;
        correction.correctedBy = correctedBy;
        correction.reason = normalizedReason;
        return correction;
    }
}
