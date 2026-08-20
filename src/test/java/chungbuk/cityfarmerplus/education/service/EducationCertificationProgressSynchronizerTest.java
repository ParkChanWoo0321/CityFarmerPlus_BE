package chungbuk.cityfarmerplus.education.service;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.education.entity.EducationCertificateSubmission;
import chungbuk.cityfarmerplus.education.entity.EducationCertification;
import chungbuk.cityfarmerplus.education.entity.EducationCourse;
import chungbuk.cityfarmerplus.education.repository.EducationCertificateSubmissionRepository;
import chungbuk.cityfarmerplus.education.repository.EducationCertificationRepository;
import chungbuk.cityfarmerplus.education.repository.EducationCourseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EducationCertificationProgressSynchronizerTest {

    @Mock
    private EducationCertificationRepository certificationRepository;

    @Mock
    private EducationCourseRepository courseRepository;

    @Mock
    private EducationCertificateSubmissionRepository submissionRepository;

    @Mock
    private EducationCertificationProgressCalculator calculator;

    @Test
    void appliesTheSharedMultiCourseCalculationToTheLockedCertification() {
        User urbanFarmer = User.register(
                "urban_15",
                "encoded",
                "도시농부",
                User.UserType.URBAN_FARMER
        );
        ReflectionTestUtils.setField(urbanFarmer, "id", 15L);
        EducationCertification certification =
                EducationCertification.create(urbanFarmer);
        ReflectionTestUtils.setField(certification, "id", 100L);
        EducationCourse course = EducationCourse.create(
                "농업안전 기초",
                "필수 교육",
                8,
                "https://example.com/course",
                true,
                1
        );
        ReflectionTestUtils.setField(course, "id", 7L);
        EducationCertificateSubmission approved =
                EducationCertificateSubmission.createPending(
                        certification,
                        course,
                        1,
                        LocalDate.of(2026, 8, 1),
                        8
                );
        Instant reviewedAt = Instant.parse("2026-08-03T00:00:00Z");
        approved.approve(
                User.registerCenterAdmin("admin", "encoded", "담당자"),
                8,
                reviewedAt
        );
        List<EducationCourse> courses = List.of(course);
        List<EducationCertificateSubmission> submissions = List.of(approved);
        var result = new EducationCertificationProgressCalculator.Result(
                EducationCertification.CertificationStatus.APPROVED,
                approved,
                8,
                reviewedAt,
                true,
                1,
                1,
                Map.of(7L, approved)
        );
        when(certificationRepository.findByIdForUpdate(100L))
                .thenReturn(Optional.of(certification));
        when(courseRepository.findAllByActiveTrueOrderByDisplayOrderAscTitleAsc())
                .thenReturn(courses);
        when(submissionRepository
                .findAllByCertificationIdOrderByAttemptNumberDesc(100L))
                .thenReturn(submissions);
        when(calculator.calculate(courses, submissions)).thenReturn(result);

        service().synchronizeLocked(100L);

        assertThat(certification.getStatus())
                .isEqualTo(EducationCertification.CertificationStatus.APPROVED);
        assertThat(certification.getApprovedSubmission()).isSameAs(approved);
        assertThat(certification.getRecognizedHours()).isEqualTo(8);
        assertThat(certification.getApprovedAt()).isEqualTo(reviewedAt);
    }

    private EducationCertificationProgressSynchronizer service() {
        return new EducationCertificationProgressSynchronizer(
                certificationRepository,
                courseRepository,
                submissionRepository,
                calculator
        );
    }
}
