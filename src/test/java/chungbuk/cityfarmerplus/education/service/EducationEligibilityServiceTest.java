package chungbuk.cityfarmerplus.education.service;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.common.exception.DomainException;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EducationEligibilityServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long CERTIFICATION_ID = 10L;

    @Mock
    private EducationCourseRepository courseRepository;

    @Mock
    private EducationCertificateSubmissionRepository submissionRepository;

    @Mock
    private EducationCertificationRepository certificationRepository;

    @Test
    void latestApprovedMandatoryCoursePasses() {
        Fixture fixture = fixture();
        stub(fixture, List.of(approved(fixture, 1, 8)));

        service().requireApproved(USER_ID);
    }

    @Test
    void latestRejectedAttemptCannotBeBypassedByAnOlderApproval() {
        Fixture fixture = fixture();
        EducationCertificateSubmission oldApproved = approved(fixture, 1, 8);
        EducationCertificateSubmission latestRejected =
                EducationCertificateSubmission.createPending(
                        fixture.certification(),
                        fixture.course(),
                        2,
                        LocalDate.of(2026, 8, 2),
                        8
                );
        latestRejected.reject(
                admin("admin_2"),
                "수료 정보 확인 필요",
                Instant.parse("2026-08-04T00:00:00Z")
        );
        stub(fixture, List.of(latestRejected, oldApproved));

        assertThatThrownBy(() -> service().requireApproved(USER_ID))
                .isInstanceOf(DomainException.class)
                .extracting("code")
                .isEqualTo("EDUCATION_CERTIFICATION_REQUIRED");
    }

    @Test
    void noConfiguredMandatoryCourseDoesNotGrantEligibility() {
        Fixture fixture = fixture();
        when(certificationRepository.findByUrbanFarmerId(USER_ID))
                .thenReturn(Optional.of(fixture.certification()));
        when(courseRepository.findAllByActiveTrueOrderByDisplayOrderAscTitleAsc())
                .thenReturn(List.of());
        when(submissionRepository
                .findAllByCertificationIdOrderByAttemptNumberDesc(CERTIFICATION_ID))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service().requireApproved(USER_ID))
                .isInstanceOf(DomainException.class)
                .extracting("code")
                .isEqualTo("EDUCATION_CERTIFICATION_REQUIRED");
    }

    private void stub(
            Fixture fixture,
            List<EducationCertificateSubmission> submissions
    ) {
        when(certificationRepository.findByUrbanFarmerId(USER_ID))
                .thenReturn(Optional.of(fixture.certification()));
        when(courseRepository.findAllByActiveTrueOrderByDisplayOrderAscTitleAsc())
                .thenReturn(List.of(fixture.course()));
        when(submissionRepository
                .findAllByCertificationIdOrderByAttemptNumberDesc(CERTIFICATION_ID))
                .thenReturn(submissions);
    }

    private EducationCertificateSubmission approved(
            Fixture fixture,
            int attempt,
            int recognizedHours
    ) {
        EducationCertificateSubmission submission =
                EducationCertificateSubmission.createPending(
                        fixture.certification(),
                        fixture.course(),
                        attempt,
                        LocalDate.of(2026, 8, attempt),
                        8
                );
        submission.approve(
                admin("admin_" + attempt),
                recognizedHours,
                Instant.parse("2026-08-03T00:00:00Z")
        );
        return submission;
    }

    private Fixture fixture() {
        User urbanFarmer = User.register(
                "urban_1",
                "encoded",
                "도시농부",
                User.UserType.URBAN_FARMER
        );
        ReflectionTestUtils.setField(urbanFarmer, "id", USER_ID);
        EducationCertification certification =
                EducationCertification.create(urbanFarmer);
        ReflectionTestUtils.setField(
                certification,
                "id",
                CERTIFICATION_ID
        );
        EducationCourse course = EducationCourse.create(
                "농업안전 기초",
                "필수 교육",
                8,
                "https://example.com/course",
                true,
                1
        );
        ReflectionTestUtils.setField(course, "id", 7L);
        return new Fixture(certification, course);
    }

    private User admin(String loginId) {
        return User.registerCenterAdmin(loginId, "encoded", "담당자");
    }

    private EducationEligibilityService service() {
        return new EducationEligibilityService(
                courseRepository,
                submissionRepository,
                certificationRepository,
                new EducationCertificationProgressCalculator()
        );
    }

    private record Fixture(
            EducationCertification certification,
            EducationCourse course
    ) {
    }
}
