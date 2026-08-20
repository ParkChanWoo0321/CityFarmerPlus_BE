package chungbuk.cityfarmerplus.jobposting.service;

import chungbuk.cityfarmerplus.jobposting.exception.JobPostingException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Component
public class JobPostingScheduleValidator {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    public void validate(
            LocalDate workDate,
            LocalTime startTime,
            LocalTime endTime
    ) {
        validate(workDate, startTime, endTime, ZonedDateTime.now(SERVICE_ZONE));
    }

    void validate(
            LocalDate workDate,
            LocalTime startTime,
            LocalTime endTime,
            ZonedDateTime now
    ) {
        if (workDate == null || startTime == null || endTime == null || now == null) {
            throw JobPostingException.invalidDetails(
                    "작업 날짜와 시작·종료 시간은 필수입니다."
            );
        }
        if (!endTime.isAfter(startTime)) {
            throw JobPostingException.invalidDetails(
                    "종료 시간은 시작 시간보다 늦어야 합니다."
            );
        }
        if (!workDate.atTime(startTime).isAfter(now.toLocalDateTime())) {
            throw JobPostingException.pastWorkDate();
        }
    }
}
