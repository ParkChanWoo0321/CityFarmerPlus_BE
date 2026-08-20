package chungbuk.cityfarmerplus.education.service;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.education.dto.EducationCertificationResponse;
import chungbuk.cityfarmerplus.education.entity.EducationCertificateSubmission;
import chungbuk.cityfarmerplus.education.entity.EducationCertification;
import chungbuk.cityfarmerplus.education.entity.EducationCourse;
import chungbuk.cityfarmerplus.education.repository.EducationCertificateSubmissionRepository;
import chungbuk.cityfarmerplus.education.repository.EducationCourseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EducationProgressServiceTest {

    private static final Long USER_ID = 15L;

    @Mock
    private EducationCourseRepository courseRepository;

    @Mock
    private EducationCertificateSubmissionRepository submissionRepository;

    @Test
    void allMandatoryCoursesMustBeApprovedAndTheirHoursAreAggregated() {
        EducationCertification certification = EducationCertification.create(urbanFarmer());
        ReflectionTestUtils.setField(certification, "id", 50L);
        EducationCourse basic = mandatoryCourse(1L, "농업안전 기초", 8, 1);
        EducationCourse advanced = mandatoryCourse(2L, "도시농업 실무", 16, 2);
        EducationCertificateSubmission basicSubmission = approvedSubmission(
                101L,
                certification,
                basic,
                1,
                8,
                Instant.parse("2026-08-01T00:00:00Z")
        );
        EducationCertificateSubmission advancedSubmission = approvedSubmission(
                102L,
                certification,
                advanced,
                2,
                16,
                Instant.parse("2026-08-02T00:00:00Z")
        );
        when(courseRepository.findAllByActiveTrueOrderByDisplayOrderAscTitleAsc())
                .thenReturn(List.of(basic, advanced));
        when(submissionRepository
                .findAllByCertificationIdOrderByAttemptNumberDesc(
                        certification.getId()
                ))
                .thenReturn(List.of(advancedSubmission, basicSubmission));
        EducationProgressService service = new EducationProgressService(
                courseRepository,
                submissionRepository,
                new EducationCertificationProgressCalculator()
        );

        EducationCertificationResponse response = service.getProgress(
                USER_ID,
                certification
        );

        assertThat(response.status())
                .isEqualTo(EducationCertification.CertificationStatus.APPROVED);
        assertThat(response.eligibleToApply()).isTrue();
        assertThat(response.requiredCourseCount()).isEqualTo(2);
        assertThat(response.approvedRequiredCourseCount()).isEqualTo(2);
        assertThat(response.recognizedHours()).isEqualTo(24);
        assertThat(response.approvedSubmissionId()).isEqualTo(102L);
        assertThat(response.approvedAt())
                .isEqualTo(Instant.parse("2026-08-02T00:00:00Z"));
        assertThat(response.courses())
                .extracting(progress -> progress.courseId())
                .containsExactly(1L, 2L);
    }

    @Test
    void approvedSubmissionWithInsufficientRecognizedHoursDoesNotCompleteCourse() {
        EducationCertification certification = EducationCertification.create(urbanFarmer());
        ReflectionTestUtils.setField(certification, "id", 50L);
        EducationCourse basic = mandatoryCourse(1L, "농업안전 기초", 8, 1);
        EducationCourse advanced = mandatoryCourse(2L, "도시농업 실무", 8, 2);
        EducationCertificateSubmission basicSubmission = approvedSubmission(
                101L,
                certification,
                basic,
                1,
                8,
                Instant.parse("2026-08-01T00:00:00Z")
        );
        EducationCertificateSubmission insufficient = approvedSubmission(
                102L,
                certification,
                advanced,
                2,
                8,
                Instant.parse("2026-08-02T00:00:00Z")
        );
        advanced.update(
                "도시농업 실무",
                "필수 교육",
                16,
                "https://example.com/2",
                true,
                2
        );
        when(courseRepository.findAllByActiveTrueOrderByDisplayOrderAscTitleAsc())
                .thenReturn(List.of(basic, advanced));
        when(submissionRepository
                .findAllByCertificationIdOrderByAttemptNumberDesc(
                        certification.getId()
                ))
                .thenReturn(List.of(insufficient, basicSubmission));
        EducationProgressService service = new EducationProgressService(
                courseRepository,
                submissionRepository,
                new EducationCertificationProgressCalculator()
        );

        EducationCertificationResponse response = service.getProgress(
                USER_ID,
                certification
        );

        assertThat(response.status())
                .isEqualTo(EducationCertification.CertificationStatus.PARTIALLY_APPROVED);
        assertThat(response.eligibleToApply()).isFalse();
        assertThat(response.requiredCourseCount()).isEqualTo(2);
        assertThat(response.approvedRequiredCourseCount()).isEqualTo(1);
        assertThat(response.recognizedHours()).isEqualTo(8);
        assertThat(response.approvedSubmissionId()).isNull();
        assertThat(response.approvedAt()).isNull();
        assertThat(response.courses())
                .filteredOn(progress -> progress.courseId().equals(2L))
                .singleElement()
                .satisfies(progress -> {
                    assertThat(progress.latestSubmissionStatus())
                            .isEqualTo(EducationCertificateSubmission.SubmissionStatus.APPROVED);
                    assertThat(progress.recognizedHours()).isEqualTo(8);
                });
    }

    private User urbanFarmer() {
        return User.register(
                "urban_1",
                "encoded",
                "도시농부",
                User.UserType.URBAN_FARMER
        );
    }

    private EducationCourse mandatoryCourse(
            Long id,
            String title,
            int requiredHours,
            int displayOrder
    ) {
        EducationCourse course = EducationCourse.create(
                title,
                "필수 교육",
                requiredHours,
                "https://example.com/" + id,
                true,
                displayOrder
        );
        ReflectionTestUtils.setField(course, "id", id);
        return course;
    }

    private EducationCertificateSubmission approvedSubmission(
            Long id,
            EducationCertification certification,
            EducationCourse course,
            int attemptNumber,
            int recognizedHours,
            Instant reviewedAt
    ) {
        EducationCertificateSubmission submission =
                EducationCertificateSubmission.createPending(
                        certification,
                        course,
                        attemptNumber,
                        LocalDate.of(2026, 7, attemptNumber),
                        Math.max(8, course.getRequiredHours())
                );
        submission.approve(
                User.registerCenterAdmin("admin_" + attemptNumber, "encoded", "담당자"),
                recognizedHours,
                reviewedAt
        );
        ReflectionTestUtils.setField(submission, "id", id);
        return submission;
    }
}
