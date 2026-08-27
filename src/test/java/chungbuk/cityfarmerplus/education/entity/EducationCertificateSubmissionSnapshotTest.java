package chungbuk.cityfarmerplus.education.entity;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.education.dto.EducationSubmissionResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class EducationCertificateSubmissionSnapshotTest {

    @Test
    void reviewUsesRequiredHoursCapturedAtSubmissionInsteadOfChangedCourseValue() {
        EducationCourse course = EducationCourse.create(
                "농업안전 기초",
                "필수 교육",
                8,
                "https://example.com/course",
                true,
                1
        );
        EducationCertificateSubmission submission =
                EducationCertificateSubmission.createPending(
                        EducationCertification.create(urbanFarmer()),
                        course,
                        1,
                        LocalDate.of(2026, 8, 1),
                        8
                );

        course.update(
                "농업안전 기초",
                "필수 교육",
                16,
                "https://example.com/course",
                true,
                1
        );
        submission.approve(
                User.registerCenterAdmin("admin", "encoded", "담당자"),
                8,
                Instant.parse("2026-08-03T00:00:00Z")
        );

        assertThat(submission.getRequiredHoursSnapshot()).isEqualTo(8);
        assertThat(submission.getStatus())
                .isEqualTo(EducationCertificateSubmission.SubmissionStatus.APPROVED);
        assertThat(EducationSubmissionResponse.from(submission)
                .requiredHoursSnapshot()).isEqualTo(8);
    }

    private User urbanFarmer() {
        return User.register(
                "urban_1",
                "encoded",
                "도시농부",
                User.UserType.URBAN_FARMER
        );
    }
}
