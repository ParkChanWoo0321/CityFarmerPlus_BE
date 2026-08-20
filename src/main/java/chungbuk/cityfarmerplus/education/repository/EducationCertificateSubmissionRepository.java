package chungbuk.cityfarmerplus.education.repository;

import chungbuk.cityfarmerplus.education.entity.EducationCertificateSubmission;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EducationCertificateSubmissionRepository
        extends JpaRepository<EducationCertificateSubmission, Long> {

    @Query("""
            select coalesce(max(submission.attemptNumber), 0)
            from EducationCertificateSubmission submission
            where submission.certification.id = :certificationId
            """)
    int findMaxAttemptNumberByCertificationId(
            @Param("certificationId") Long certificationId
    );

    @EntityGraph(attributePaths = {"course", "reviewedBy", "documents"})
    List<EducationCertificateSubmission>
    findAllByCertificationUrbanFarmerIdOrderByAttemptNumberDesc(Long userId);

    @EntityGraph(attributePaths = {"course"})
    List<EducationCertificateSubmission>
    findAllByCertificationIdOrderByAttemptNumberDesc(Long certificationId);

    @EntityGraph(attributePaths = {"course", "reviewedBy", "documents"})
    Optional<EducationCertificateSubmission>
    findByIdAndCertificationUrbanFarmerId(Long id, Long userId);

    Optional<EducationCertificateSubmission>
    findTopByCertificationIdAndCourseIdOrderByAttemptNumberDesc(
            Long certificationId,
            Long courseId
    );

    /**
     * 담당자 심사 목록 계약이다. 탈퇴·정지 계정의 제출은 이 목록에서 제외한다.
     */
    @Query("""
            select submission
            from EducationCertificateSubmission submission
            where submission.status = :status
              and submission.certification.urbanFarmer.accountStatus = chungbuk.cityfarmerplus.auth.entity.User.AccountStatus.ACTIVE
            """)
    Page<EducationCertificateSubmission> findAllByStatus(
            @Param("status") EducationCertificateSubmission.SubmissionStatus status,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"course", "reviewedBy", "documents", "certification.urbanFarmer"})
    @Query("""
            select submission
            from EducationCertificateSubmission submission
            where submission.id = :submissionId
            """)
    Optional<EducationCertificateSubmission> findDetailedById(
            @Param("submissionId") Long submissionId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select submission
            from EducationCertificateSubmission submission
            join fetch submission.certification certification
            where submission.id = :submissionId
              and certification.urbanFarmer.accountStatus = chungbuk.cityfarmerplus.auth.entity.User.AccountStatus.ACTIVE
            """)
    Optional<EducationCertificateSubmission> findByIdForUpdate(
            @Param("submissionId") Long submissionId
    );

    @Query("""
            select count(submission)
            from EducationCertificateSubmission submission
            where submission.status = :status
              and submission.certification.urbanFarmer.accountStatus = chungbuk.cityfarmerplus.auth.entity.User.AccountStatus.ACTIVE
            """)
    long countByStatus(
            @Param("status") EducationCertificateSubmission.SubmissionStatus status
    );
}
