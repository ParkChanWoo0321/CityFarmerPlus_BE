package chungbuk.cityfarmerplus.education.entity;

import chungbuk.cityfarmerplus.auth.entity.User;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(
        name = "education_certificate_submissions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_education_submission_attempt",
                columnNames = {"certification_id", "attempt_number"}
        ),
        indexes = {
                @Index(name = "idx_education_submission_status", columnList = "status"),
                @Index(name = "idx_education_submission_time", columnList = "submitted_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EducationCertificateSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "certification_id", nullable = false, updatable = false)
    private EducationCertification certification;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "education_course_id", nullable = false, updatable = false)
    private EducationCourse course;

    @Column(name = "course_title_snapshot", nullable = false, length = 150, updatable = false)
    private String courseTitleSnapshot;

    @Column(name = "attempt_number", nullable = false, updatable = false)
    private int attemptNumber;

    @Column(name = "completion_date", nullable = false, updatable = false)
    private LocalDate completionDate;

    @Column(name = "completion_hours", nullable = false, updatable = false)
    private int completionHours;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubmissionStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "recognized_hours")
    private Integer recognizedHours;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @OneToMany(
            mappedBy = "submission",
            cascade = CascadeType.PERSIST,
            orphanRemoval = false
    )
    @OrderBy("displayOrder ASC")
    private List<EducationCertificateDocument> documents = new ArrayList<>();

    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    public static EducationCertificateSubmission createPending(
            EducationCertification certification,
            EducationCourse course,
            int attemptNumber,
            LocalDate completionDate,
            int completionHours
    ) {
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("제출 회차는 1 이상이어야 합니다.");
        }
        if (completionHours < 8) {
            throw new IllegalArgumentException("교육 이수 시간은 8시간 이상이어야 합니다.");
        }
        EducationCertificateSubmission submission = new EducationCertificateSubmission();
        submission.certification = certification;
        submission.course = course;
        submission.courseTitleSnapshot = course.getTitle();
        submission.attemptNumber = attemptNumber;
        submission.completionDate = completionDate;
        submission.completionHours = completionHours;
        submission.status = SubmissionStatus.PENDING_REVIEW;
        return submission;
    }

    public void addDocument(
            String originalFilename,
            String storageKey,
            String contentType,
            long sizeBytes,
            String sha256
    ) {
        documents.add(EducationCertificateDocument.create(
                this,
                documents.size(),
                originalFilename,
                storageKey,
                contentType,
                sizeBytes,
                sha256
        ));
    }

    public void approve(User reviewer, int recognizedHours, Instant now) {
        validatePendingReviewer(reviewer);
        int minimumHours = Math.max(8, course.getRequiredHours());
        if (recognizedHours < minimumHours || recognizedHours > completionHours) {
            throw new IllegalArgumentException(
                    "인정 교육 시간은 과정 필수 시간 이상이며 제출한 이수 시간을 초과할 수 없습니다."
            );
        }
        status = SubmissionStatus.APPROVED;
        reviewedBy = reviewer;
        reviewedAt = now;
        this.recognizedHours = recognizedHours;
        rejectionReason = null;
    }

    public void reject(User reviewer, String reason, Instant now) {
        validatePendingReviewer(reviewer);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("반려 사유를 입력해야 합니다.");
        }
        status = SubmissionStatus.REJECTED;
        reviewedBy = reviewer;
        reviewedAt = now;
        recognizedHours = null;
        rejectionReason = reason;
    }

    public List<EducationCertificateDocument> getDocuments() {
        return Collections.unmodifiableList(documents);
    }

    private void validatePendingReviewer(User reviewer) {
        if (reviewer.getUserType() != User.UserType.CENTER_ADMIN) {
            throw new IllegalArgumentException("담당자만 교육 인증을 심사할 수 있습니다.");
        }
        if (status != SubmissionStatus.PENDING_REVIEW) {
            throw new IllegalStateException("검토 중인 이수증만 심사할 수 있습니다.");
        }
    }

    public enum SubmissionStatus {
        PENDING_REVIEW,
        APPROVED,
        REJECTED
    }
}
