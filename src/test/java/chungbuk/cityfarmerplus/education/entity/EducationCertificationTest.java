package chungbuk.cityfarmerplus.education.entity;

import chungbuk.cityfarmerplus.auth.entity.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EducationCertificationTest {

    @Test
    void synchronizesApprovedProgressOnlyAfterAllCourseResultsAreCalculated() {
        EducationCertification certification = EducationCertification.create(urbanFarmer());
        EducationCertificateSubmission first =
                EducationCertificateSubmission.createPending(
                        certification,
                        course(8),
                        1,
                        LocalDate.of(2026, 7, 1),
                        8
                );
        EducationCertificateSubmission second =
                EducationCertificateSubmission.createPending(
                        certification,
                        course(12),
                        2,
                        LocalDate.of(2026, 7, 2),
                        12
                );
        User admin = User.registerCenterAdmin("admin_1", "encoded", "담당자");
        Instant firstReviewedAt = Instant.parse("2026-08-01T00:00:00Z");
        Instant completedAt = Instant.parse("2026-08-02T00:00:00Z");
        first.approve(admin, 8, firstReviewedAt);
        second.approve(admin, 12, completedAt);
        certification.synchronizeProgress(
                EducationCertification.CertificationStatus.APPROVED,
                second,
                20,
                completedAt
        );

        assertThat(certification.getStatus())
                .isEqualTo(EducationCertification.CertificationStatus.APPROVED);
        assertThat(certification.getApprovedSubmission()).isSameAs(second);
        assertThat(certification.getRecognizedHours()).isEqualTo(20);
        assertThat(certification.getApprovedAt()).isEqualTo(completedAt);
    }

    @Test
    void nonApprovedAggregateKeepsPartialHoursWithoutClaimingFullApproval() {
        EducationCertification certification = EducationCertification.create(urbanFarmer());
        certification.synchronizeProgress(
                EducationCertification.CertificationStatus.PARTIALLY_APPROVED,
                null,
                8,
                null
        );

        assertThat(certification.getStatus())
                .isEqualTo(EducationCertification.CertificationStatus.PARTIALLY_APPROVED);
        assertThat(certification.getApprovedSubmission()).isNull();
        assertThat(certification.getRecognizedHours()).isEqualTo(8);
        assertThat(certification.getApprovedAt()).isNull();
    }

    @Test
    void fullApprovalRejectsARepresentativeSubmissionFromAnotherCertification() {
        EducationCertification certification = EducationCertification.create(urbanFarmer());
        EducationCertification other = EducationCertification.create(urbanFarmer());
        EducationCertificateSubmission submission =
                EducationCertificateSubmission.createPending(
                        other,
                        course(8),
                        1,
                        LocalDate.of(2026, 7, 1),
                        8
                );
        Instant reviewedAt = Instant.parse("2026-08-01T00:00:00Z");
        submission.approve(
                User.registerCenterAdmin("admin_1", "encoded", "담당자"),
                8,
                reviewedAt
        );

        assertThatThrownBy(() -> certification.synchronizeProgress(
                EducationCertification.CertificationStatus.APPROVED,
                submission,
                8,
                reviewedAt
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lessThanEightHoursCannotCreateSubmission() {
        EducationCertification certification = EducationCertification.create(urbanFarmer());

        assertThatThrownBy(() -> EducationCertificateSubmission.createPending(
                certification,
                course(8),
                1,
                LocalDate.now(),
                7
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private User urbanFarmer() {
        return User.register(
                "urban_1",
                "encoded",
                "도시농부",
                User.UserType.URBAN_FARMER
        );
    }

    private EducationCourse course(int requiredHours) {
        return EducationCourse.create(
                "도시농업 기초",
                "필수 교육",
                requiredHours,
                "https://example.com",
                true,
                1
        );
    }
}
