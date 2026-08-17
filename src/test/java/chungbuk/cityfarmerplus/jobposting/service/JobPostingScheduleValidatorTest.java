package chungbuk.cityfarmerplus.jobposting.service;

import chungbuk.cityfarmerplus.jobposting.exception.JobPostingException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobPostingScheduleValidatorTest {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final JobPostingScheduleValidator validator =
            new JobPostingScheduleValidator();
    private final ZonedDateTime now = ZonedDateTime.of(
            2026,
            8,
            11,
            10,
            0,
            0,
            0,
            SERVICE_ZONE
    );

    @Test
    void acceptsTodayFutureStartTimeAndFutureDate() {
        assertThatCode(() -> validator.validate(
                LocalDate.of(2026, 8, 11),
                LocalTime.of(10, 1),
                LocalTime.of(18, 0),
                now
        )).doesNotThrowAnyException();

        assertThatCode(() -> validator.validate(
                LocalDate.of(2026, 8, 12),
                LocalTime.of(8, 0),
                LocalTime.of(17, 0),
                now
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsPastOrCurrentStartTime() {
        assertThatThrownBy(() -> validator.validate(
                LocalDate.of(2026, 8, 11),
                LocalTime.of(10, 0),
                LocalTime.of(18, 0),
                now
        )).isInstanceOf(JobPostingException.class)
                .extracting("code")
                .isEqualTo("PAST_WORK_DATE");

        assertThatThrownBy(() -> validator.validate(
                LocalDate.of(2026, 8, 10),
                LocalTime.of(11, 0),
                LocalTime.of(18, 0),
                now
        )).isInstanceOf(JobPostingException.class)
                .extracting("code")
                .isEqualTo("PAST_WORK_DATE");
    }

    @Test
    void rejectsEndTimeNotAfterStartTime() {
        assertThatThrownBy(() -> validator.validate(
                LocalDate.of(2026, 8, 12),
                LocalTime.of(10, 0),
                LocalTime.of(10, 0),
                now
        )).isInstanceOf(JobPostingException.class)
                .extracting("code")
                .isEqualTo("INVALID_JOB_POSTING_DETAILS");
    }

    @Test
    void rejectsMissingScheduleValuesAsDomainError() {
        assertThatThrownBy(() -> validator.validate(
                null,
                LocalTime.of(10, 0),
                LocalTime.of(18, 0),
                now
        )).isInstanceOf(JobPostingException.class)
                .extracting("code")
                .isEqualTo("INVALID_JOB_POSTING_DETAILS");
    }
}
