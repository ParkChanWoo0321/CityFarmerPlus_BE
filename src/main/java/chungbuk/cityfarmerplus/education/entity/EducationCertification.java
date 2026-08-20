package chungbuk.cityfarmerplus.education.entity;

import chungbuk.cityfarmerplus.auth.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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

@Entity
@Table(
        name = "education_certifications",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_education_certification_user",
                columnNames = "urban_farmer_user_id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EducationCertification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "urban_farmer_user_id", nullable = false, updatable = false)
    private User urbanFarmer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CertificationStatus status;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_submission_id")
    private EducationCertificateSubmission approvedSubmission;

    @Column(name = "recognized_hours")
    private Integer recognizedHours;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static EducationCertification create(User urbanFarmer) {
        if (urbanFarmer.getUserType() != User.UserType.URBAN_FARMER) {
            throw new IllegalArgumentException("도시농부 사용자만 교육 인증을 만들 수 있습니다.");
        }
        EducationCertification certification = new EducationCertification();
        certification.urbanFarmer = urbanFarmer;
        certification.status = CertificationStatus.NOT_SUBMITTED;
        return certification;
    }

    /**
     * 여러 필수 과정의 최신 심사 결과를 모두 계산한 뒤 집계 상태를 동기화한다.
     * 단일 제출 건만으로 전체 인증을 승인하거나 반려해서는 안 된다.
     */
    public void synchronizeProgress(
            CertificationStatus resolvedStatus,
            EducationCertificateSubmission representativeApprovedSubmission,
            Integer recognizedHours,
            Instant approvedAt
    ) {
        if (resolvedStatus == null) {
            throw new IllegalArgumentException("교육 인증 집계 상태가 필요합니다.");
        }
        if (resolvedStatus == CertificationStatus.APPROVED) {
            if (representativeApprovedSubmission == null
                    || representativeApprovedSubmission.getCertification() != this
                    || representativeApprovedSubmission.getStatus()
                    != EducationCertificateSubmission.SubmissionStatus.APPROVED
                    || recognizedHours == null
                    || recognizedHours < 1
                    || approvedAt == null) {
                throw new IllegalArgumentException(
                        "승인된 교육 인증의 집계 정보가 올바르지 않습니다."
                );
            }
            status = resolvedStatus;
            approvedSubmission = representativeApprovedSubmission;
            this.recognizedHours = recognizedHours;
            this.approvedAt = approvedAt;
            return;
        }
        status = resolvedStatus;
        approvedSubmission = null;
        this.recognizedHours = recognizedHours != null && recognizedHours > 0
                ? recognizedHours
                : null;
        this.approvedAt = null;
    }

    public enum CertificationStatus {
        NOT_SUBMITTED,
        PENDING_REVIEW,
        PARTIALLY_APPROVED,
        APPROVED,
        REJECTED
    }
}
