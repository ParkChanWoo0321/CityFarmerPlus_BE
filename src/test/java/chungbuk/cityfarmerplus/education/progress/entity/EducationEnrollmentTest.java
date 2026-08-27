package chungbuk.cityfarmerplus.education.progress.entity;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.education.entity.EducationCourse;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EducationEnrollmentTest {

    private static final Instant FIRST_EVENT = Instant.parse("2026-08-28T00:00:00Z");

    @Test
    void calculatesPercentageAndRemainingMinutesFromProviderProgress() {
        EducationEnrollment enrollment = enrollment(480, 240);

        assertThat(enrollment.getProgressStatus())
                .isEqualTo(EducationEnrollment.ProgressStatus.IN_PROGRESS);
        assertThat(enrollment.remainingMinutes()).isEqualTo(240);
        assertThat(enrollment.progressPercentage()).isEqualTo(50);
        assertThat(enrollment.getStartedAt()).isEqualTo(FIRST_EVENT);
        assertThat(enrollment.getCompletedAt()).isNull();
        assertThat(enrollment(480, 479).progressPercentage()).isEqualTo(99);
    }

    @Test
    void olderEventIsAuditedButDoesNotOverwriteCurrentProgress() {
        EducationEnrollment enrollment = enrollment(480, 240);
        Instant laterSync = Instant.parse("2026-08-28T00:10:00Z");

        boolean applied = enrollment.applyProgress(
                480,
                120,
                FIRST_EVENT.minusSeconds(60),
                laterSync
        );

        assertThat(applied).isFalse();
        assertThat(enrollment.getCompletedMinutes()).isEqualTo(240);
        assertThat(enrollment.getProviderUpdatedAt()).isEqualTo(FIRST_EVENT);
        assertThat(enrollment.getLastSyncedAt()).isEqualTo(laterSync);
    }

    @Test
    void newerEventCannotReduceProgressOrReopenCompletedCourse() {
        EducationEnrollment inProgress = enrollment(480, 240);
        EducationEnrollment completed = enrollment(480, 480);

        assertThatThrownBy(() -> inProgress.applyProgress(
                480,
                239,
                FIRST_EVENT.plusSeconds(60),
                FIRST_EVENT.plusSeconds(61)
        )).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> completed.applyProgress(
                600,
                480,
                FIRST_EVENT.plusSeconds(60),
                FIRST_EVENT.plusSeconds(61)
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void completionIsRecordedAtTheFirstFullProgressEvent() {
        EducationEnrollment enrollment = enrollment(480, 240);
        Instant completedAt = FIRST_EVENT.plusSeconds(120);

        boolean applied = enrollment.applyProgress(
                480,
                480,
                completedAt,
                completedAt.plusSeconds(1)
        );

        assertThat(applied).isTrue();
        assertThat(enrollment.getProgressStatus())
                .isEqualTo(EducationEnrollment.ProgressStatus.COMPLETED);
        assertThat(enrollment.getCompletedAt()).isEqualTo(completedAt);
        assertThat(enrollment.progressPercentage()).isEqualTo(100);
    }

    private EducationEnrollment enrollment(int totalMinutes, int completedMinutes) {
        return EducationEnrollment.create(
                User.register("urban_1", "encoded", "도시농부", User.UserType.URBAN_FARMER),
                EducationCourse.create(
                        "도시농업 기초",
                        "필수 교육",
                        8,
                        "https://example.com",
                        true,
                        1
                ),
                "CHUNGBUK_LMS",
                "enrollment-21-1",
                totalMinutes,
                completedMinutes,
                FIRST_EVENT,
                FIRST_EVENT.plusSeconds(1)
        );
    }
}
