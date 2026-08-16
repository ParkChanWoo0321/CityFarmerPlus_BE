package chungbuk.cityfarmerplus.education.service;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.common.exception.DomainException;
import chungbuk.cityfarmerplus.education.dto.EducationSubmissionRequest;
import chungbuk.cityfarmerplus.education.dto.EducationSubmissionResponse;
import chungbuk.cityfarmerplus.education.entity.EducationCertificateSubmission;
import chungbuk.cityfarmerplus.education.entity.EducationCertification;
import chungbuk.cityfarmerplus.education.entity.EducationCourse;
import chungbuk.cityfarmerplus.education.repository.EducationCertificateSubmissionRepository;
import chungbuk.cityfarmerplus.education.repository.EducationCertificationRepository;
import chungbuk.cityfarmerplus.education.repository.EducationCourseRepository;
import chungbuk.cityfarmerplus.urbanfarmer.service.UserRoleAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EducationSubmissionTransactionServiceTest {

    private static final Long USER_ID = 15L;
    private static final Long COURSE_ID = 7L;

    @Mock
    private EducationCertificationRepository certificationRepository;

    @Mock
    private EducationCertificateSubmissionRepository submissionRepository;

    @Mock
    private EducationCourseRepository courseRepository;

    @Mock
    private UserRoleAccessService accessService;

    @Mock
    private EducationCertificationProgressSynchronizer progressSynchronizer;

    private final EducationSubmissionPolicy submissionPolicy =
            new EducationSubmissionPolicy();

    @Test
    void firstSubmissionCreatesCertificationAndPendingAttemptWithDocuments() {
        User urbanFarmer = urbanFarmer();
        EducationCourse course = course();
        stubLockedUserAndCourse(urbanFarmer, course);
        when(certificationRepository.findByUrbanFarmerIdForUpdate(USER_ID))
                .thenReturn(Optional.empty());
        when(certificationRepository.saveAndFlush(any(EducationCertification.class)))
                .thenAnswer(invocation -> persistedCertification(
                        invocation.getArgument(0)
                ));
        when(submissionRepository
                .findTopByCertificationIdAndCourseIdOrderByAttemptNumberDesc(
                        100L,
                        COURSE_ID
                ))
                .thenReturn(Optional.empty());
        when(submissionRepository.findMaxAttemptNumberByCertificationId(100L))
                .thenReturn(0);
        when(submissionRepository.saveAndFlush(
                any(EducationCertificateSubmission.class)
        )).thenAnswer(invocation -> persistedSubmission(invocation.getArgument(0)));

        EducationSubmissionResponse response = service().persist(
                USER_ID,
                request(),
                List.of(
                        stored("first-key", "첫 번째.pdf"),
                        stored("second-key", "두 번째.png")
                )
        );

        assertThat(response.id()).isEqualTo(200L);
        assertThat(response.attemptNumber()).isEqualTo(1);
        assertThat(response.status())
                .isEqualTo(EducationCertificateSubmission.SubmissionStatus.PENDING_REVIEW);
        assertThat(response.courseId()).isEqualTo(COURSE_ID);
        assertThat(response.courseTitle()).isEqualTo("농업안전 기초");
        assertThat(response.documents())
                .extracting(document -> document.originalFilename())
                .containsExactly("첫 번째.pdf", "두 번째.png");

        verify(progressSynchronizer).synchronizeLocked(100L);
    }

    @Test
    void pendingLatestSubmissionForTheSameCourseBlocksDuplicateSubmission() {
        User urbanFarmer = urbanFarmer();
        EducationCourse course = course();
        EducationCertification certification = certification(urbanFarmer);
        EducationCertificateSubmission pending = pending(
                certification,
                course,
                1
        );
        stubLockedUserAndCourse(urbanFarmer, course);
        when(certificationRepository.findByUrbanFarmerIdForUpdate(USER_ID))
                .thenReturn(Optional.of(certification));
        when(submissionRepository
                .findTopByCertificationIdAndCourseIdOrderByAttemptNumberDesc(
                        100L,
                        COURSE_ID
                ))
                .thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service().persist(
                USER_ID,
                request(),
                List.of(stored("key-1", "certificate.pdf"))
        )).isInstanceOf(DomainException.class)
                .extracting("code")
                .isEqualTo("EDUCATION_SUBMISSION_NOT_ALLOWED");

        verify(submissionRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectedCourseSubmissionAllowsANewAttemptWithoutChangingHistory() {
        User urbanFarmer = urbanFarmer();
        EducationCourse course = course();
        EducationCertification certification = certification(urbanFarmer);
        EducationCertificateSubmission rejected = pending(
                certification,
                course,
                1
        );
        rejected.reject(
                User.registerCenterAdmin("admin", "encoded", "담당자"),
                "수료일 확인 필요",
                Instant.parse("2026-08-02T00:00:00Z")
        );
        stubLockedUserAndCourse(urbanFarmer, course);
        when(certificationRepository.findByUrbanFarmerIdForUpdate(USER_ID))
                .thenReturn(Optional.of(certification));
        when(submissionRepository
                .findTopByCertificationIdAndCourseIdOrderByAttemptNumberDesc(
                        100L,
                        COURSE_ID
                ))
                .thenReturn(Optional.of(rejected));
        when(submissionRepository.findMaxAttemptNumberByCertificationId(100L))
                .thenReturn(1);
        when(submissionRepository.saveAndFlush(
                any(EducationCertificateSubmission.class)
        )).thenAnswer(invocation -> persistedSubmission(invocation.getArgument(0)));

        EducationSubmissionResponse response = service().persist(
                USER_ID,
                request(),
                List.of(stored("key-2", "new-certificate.pdf"))
        );

        assertThat(response.attemptNumber()).isEqualTo(2);
        assertThat(response.status())
                .isEqualTo(EducationCertificateSubmission.SubmissionStatus.PENDING_REVIEW);
        assertThat(rejected.getStatus())
                .isEqualTo(EducationCertificateSubmission.SubmissionStatus.REJECTED);
    }

    @Test
    void approvedSubmissionCanBeReplacedWhenCurrentRequiredHoursIncrease() {
        User urbanFarmer = urbanFarmer();
        EducationCourse course = course();
        EducationCertification certification = certification(urbanFarmer);
        EducationCertificateSubmission approved = pending(
                certification,
                course,
                1
        );
        approved.approve(
                User.registerCenterAdmin("admin", "encoded", "담당자"),
                8,
                Instant.parse("2026-08-02T00:00:00Z")
        );
        ReflectionTestUtils.setField(course, "requiredHours", 16);
        stubLockedUserAndCourse(urbanFarmer, course);
        when(certificationRepository.findByUrbanFarmerIdForUpdate(USER_ID))
                .thenReturn(Optional.of(certification));
        when(submissionRepository
                .findTopByCertificationIdAndCourseIdOrderByAttemptNumberDesc(
                        100L,
                        COURSE_ID
                ))
                .thenReturn(Optional.of(approved));
        when(submissionRepository.findMaxAttemptNumberByCertificationId(100L))
                .thenReturn(1);
        when(submissionRepository.saveAndFlush(
                any(EducationCertificateSubmission.class)
        )).thenAnswer(invocation -> persistedSubmission(invocation.getArgument(0)));

        EducationSubmissionResponse response = service().persist(
                USER_ID,
                request(16),
                List.of(stored("key-2", "new-certificate.pdf"))
        );

        assertThat(response.attemptNumber()).isEqualTo(2);
        assertThat(response.completionHours()).isEqualTo(16);
        assertThat(approved.getStatus())
                .isEqualTo(EducationCertificateSubmission.SubmissionStatus.APPROVED);
    }

    @Test
    void databaseConstraintConflictUsesEducationErrorContract() {
        User urbanFarmer = urbanFarmer();
        EducationCourse course = course();
        EducationCertification certification = certification(urbanFarmer);
        stubLockedUserAndCourse(urbanFarmer, course);
        when(certificationRepository.findByUrbanFarmerIdForUpdate(USER_ID))
                .thenReturn(Optional.of(certification));
        when(submissionRepository
                .findTopByCertificationIdAndCourseIdOrderByAttemptNumberDesc(
                        100L,
                        COURSE_ID
                ))
                .thenReturn(Optional.empty());
        when(submissionRepository.findMaxAttemptNumberByCertificationId(100L))
                .thenReturn(0);
        when(submissionRepository.saveAndFlush(
                any(EducationCertificateSubmission.class)
        )).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service().persist(
                USER_ID,
                request(),
                List.of(stored("key-1", "certificate.pdf"))
        )).isInstanceOf(DomainException.class)
                .extracting("code")
                .isEqualTo("EDUCATION_SUBMISSION_DATA_CONFLICT");
    }

    private void stubLockedUserAndCourse(
            User urbanFarmer,
            EducationCourse course
    ) {
        when(accessService.requireUrbanFarmerForUpdate(USER_ID))
                .thenReturn(urbanFarmer);
        when(courseRepository.findById(COURSE_ID))
                .thenReturn(Optional.of(course));
    }

    private EducationSubmissionTransactionService service() {
        return new EducationSubmissionTransactionService(
                certificationRepository,
                submissionRepository,
                courseRepository,
                accessService,
                submissionPolicy,
                progressSynchronizer
        );
    }

    private User urbanFarmer() {
        User user = User.register(
                "urban_15",
                "encoded",
                "도시농부",
                User.UserType.URBAN_FARMER
        );
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

    private EducationCourse course() {
        EducationCourse course = EducationCourse.create(
                "농업안전 기초",
                "필수 교육",
                8,
                "https://example.com/course",
                true,
                1
        );
        ReflectionTestUtils.setField(course, "id", COURSE_ID);
        return course;
    }

    private EducationCertification certification(User urbanFarmer) {
        return persistedCertification(EducationCertification.create(urbanFarmer));
    }

    private EducationCertification persistedCertification(
            EducationCertification certification
    ) {
        ReflectionTestUtils.setField(certification, "id", 100L);
        return certification;
    }

    private EducationCertificateSubmission pending(
            EducationCertification certification,
            EducationCourse course,
            int attemptNumber
    ) {
        return EducationCertificateSubmission.createPending(
                certification,
                course,
                attemptNumber,
                LocalDate.of(2026, 8, 1),
                8
        );
    }

    private EducationCertificateSubmission persistedSubmission(
            EducationCertificateSubmission submission
    ) {
        ReflectionTestUtils.setField(submission, "id", 200L);
        ReflectionTestUtils.setField(
                submission,
                "submittedAt",
                Instant.parse("2026-08-03T00:00:00Z")
        );
        return submission;
    }

    private EducationSubmissionRequest request() {
        return request(8);
    }

    private EducationSubmissionRequest request(int completionHours) {
        return new EducationSubmissionRequest(
                COURSE_ID,
                LocalDate.of(2026, 8, 1),
                completionHours
        );
    }

    private StoredEducationDocument stored(String key, String filename) {
        return new StoredEducationDocument(
                filename,
                key,
                filename.endsWith(".png") ? "image/png" : "application/pdf",
                16L,
                "a".repeat(64)
        );
    }
}
