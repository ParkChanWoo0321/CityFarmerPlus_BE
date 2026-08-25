package chungbuk.cityfarmerplus.work.entity;

import chungbuk.cityfarmerplus.application.entity.JobApplication;
import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(
        name = "work_assignments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_work_assignments_application",
                columnNames = "job_application_id"
        ),
        indexes = {
                @Index(name = "idx_work_assignments_farmer_date", columnList = "urban_farmer_user_id,work_date"),
                @Index(name = "idx_work_assignments_farm", columnList = "farm_profile_id,status")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_application_id", nullable = false, updatable = false)
    private JobApplication jobApplication;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "urban_farmer_user_id", nullable = false, updatable = false)
    private User urbanFarmer;

    @Column(name = "farm_profile_id", nullable = false, updatable = false)
    private Long farmProfileId;

    @Column(name = "job_posting_id", nullable = false, updatable = false)
    private Long jobPostingId;

    @Column(name = "farm_name", nullable = false, length = 100, updatable = false)
    private String farmName;

    @Column(name = "farm_address", nullable = false, length = 255, updatable = false)
    private String farmAddress;

    @Column(name = "farm_contact_number", nullable = false, length = 20, updatable = false)
    private String farmContactNumber;

    @Column(nullable = false, length = 50, updatable = false)
    private String crop;

    @Column(name = "work_type", nullable = false, length = 100, updatable = false)
    private String workType;

    @Column(name = "work_date", nullable = false, updatable = false)
    private LocalDate workDate;

    @Column(name = "start_time", nullable = false, updatable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false, updatable = false)
    private LocalTime endTime;

    @Column(name = "recruitment_capacity", updatable = false)
    private Integer recruitmentCapacity;

    @Column(name = "meeting_place", nullable = false, length = 255, updatable = false)
    private String meetingPlace;

    @Column(name = "wage_amount", nullable = false, updatable = false)
    private int wageAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "wage_unit", nullable = false, length = 20, updatable = false)
    private JobPosting.WageUnit wageUnit;

    @Column(length = 1000, updatable = false)
    private String supplies;

    @Column(length = 2000, updatable = false)
    private String precautions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_status", nullable = false, length = 20)
    private AttendanceStatus attendanceStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendance_recorded_by_user_id")
    private User attendanceRecordedBy;

    @Column(name = "attendance_recorded_at")
    private Instant attendanceRecordedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static WorkAssignment fromMatchedApplication(JobApplication application) {
        if (application.getStatus() != JobApplication.ApplicationStatus.MATCHED) {
            throw new IllegalArgumentException("매칭된 지원만 근무 일정으로 만들 수 있습니다.");
        }
        JobPosting posting = application.getJobPosting();
        WorkAssignment assignment = new WorkAssignment();
        assignment.jobApplication = application;
        assignment.urbanFarmer = application.getUrbanFarmer();
        assignment.farmProfileId = posting.getFarmProfile().getId();
        assignment.jobPostingId = posting.getId();
        assignment.farmName = posting.getFarmProfile().getFarmName();
        assignment.farmAddress = posting.getFarmProfile().getFarmAddress();
        assignment.farmContactNumber = posting.getFarmProfile().getContactNumber();
        assignment.crop = posting.getCrop();
        assignment.workType = posting.getWorkType();
        assignment.workDate = posting.getWorkDate();
        assignment.startTime = posting.getStartTime();
        assignment.endTime = posting.getEndTime();
        assignment.recruitmentCapacity = posting.getCapacity();
        assignment.meetingPlace = posting.getMeetingPlace();
        assignment.wageAmount = posting.getWageAmount();
        assignment.wageUnit = posting.getWageUnit();
        assignment.supplies = posting.getSupplies();
        assignment.precautions = posting.getPrecautions();
        assignment.status = WorkStatus.SCHEDULED;
        assignment.attendanceStatus = AttendanceStatus.NOT_RECORDED;
        return assignment;
    }

    public void recordAttendance(AttendanceStatus attendance, User farmUser, Instant now) {
        if (status != WorkStatus.SCHEDULED) {
            throw new IllegalStateException("예정된 근무의 출결만 등록할 수 있습니다.");
        }
        if (attendance == AttendanceStatus.NOT_RECORDED) {
            throw new IllegalArgumentException("출근 또는 결근을 선택해야 합니다.");
        }
        if (attendanceStatus != AttendanceStatus.NOT_RECORDED) {
            throw new IllegalStateException("이미 등록된 출결은 담당자만 정정할 수 있습니다.");
        }
        attendanceStatus = attendance;
        attendanceRecordedBy = farmUser;
        attendanceRecordedAt = now;
        if (attendance == AttendanceStatus.ABSENT) {
            status = WorkStatus.NO_SHOW;
            jobApplication.markNoShow();
        }
    }

    public void completeByFarm(Instant now) {
        if (status != WorkStatus.SCHEDULED) {
            throw new IllegalStateException("예정된 근무만 완료할 수 있습니다.");
        }
        if (attendanceStatus != AttendanceStatus.PRESENT) {
            throw new IllegalStateException("출근 상태인 근무만 완료할 수 있습니다.");
        }
        status = WorkStatus.COMPLETED;
        completedAt = now;
        jobApplication.completeWork();
    }

    public AttendanceStatus correctAttendance(AttendanceStatus correctedStatus, Instant now) {
        if (status == WorkStatus.CANCELLED) {
            throw new IllegalStateException("취소된 근무의 출결은 정정할 수 없습니다.");
        }
        if (correctedStatus == AttendanceStatus.NOT_RECORDED) {
            throw new IllegalArgumentException("출근 또는 결근으로 정정해야 합니다.");
        }
        AttendanceStatus previous = attendanceStatus;
        if (previous == correctedStatus) {
            throw new IllegalStateException("기존 출결과 다른 값으로 정정해야 합니다.");
        }
        attendanceStatus = correctedStatus;
        attendanceRecordedAt = now;
        if (correctedStatus == AttendanceStatus.ABSENT) {
            if (status == WorkStatus.COMPLETED) {
                completedAt = null;
            }
            status = WorkStatus.NO_SHOW;
            jobApplication.markNoShow();
        } else if (correctedStatus == AttendanceStatus.PRESENT
                && status == WorkStatus.NO_SHOW) {
            status = WorkStatus.SCHEDULED;
            jobApplication.reopenAfterNoShow();
        }
        return previous;
    }

    public void cancel() {
        if (status == WorkStatus.COMPLETED || status == WorkStatus.CANCELLED) {
            throw new IllegalStateException("완료된 근무는 취소할 수 없습니다.");
        }
        status = WorkStatus.CANCELLED;
    }

    public enum WorkStatus {
        SCHEDULED,
        COMPLETED,
        NO_SHOW,
        CANCELLED
    }

    public enum AttendanceStatus {
        NOT_RECORDED,
        PRESENT,
        ABSENT
    }
}
