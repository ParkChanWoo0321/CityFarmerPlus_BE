package chungbuk.cityfarmerplus.jobposting.entity;

import java.time.LocalDate;
import java.time.LocalTime;

public record JobPostingDetails(
        String crop,
        String workType,
        LocalDate workDate,
        LocalTime startTime,
        LocalTime endTime,
        int capacity,
        String meetingPlace,
        int wageAmount,
        JobPosting.WageUnit wageUnit,
        String supplies,
        String precautions,
        String farmMessage,
        String applicantPreference,
        String title,
        String description,
        String beginnerGuide
) {

    public JobPostingDetails {
        crop = requiredText(crop, "작물", 50);
        workType = requiredText(workType, "작업 종류", 100);
        if (workDate == null || startTime == null || endTime == null) {
            throw new IllegalArgumentException(
                    "작업 날짜와 시작·종료 시간은 필수입니다."
            );
        }
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException(
                    "종료 시간은 시작 시간보다 늦어야 합니다."
            );
        }
        if (capacity < 1 || capacity > 1000) {
            throw new IllegalArgumentException(
                    "모집 인원은 1명 이상 1000명 이하여야 합니다."
            );
        }
        meetingPlace = requiredText(meetingPlace, "집결 장소", 255);
        if (wageAmount < 1 || wageAmount > 100_000_000) {
            throw new IllegalArgumentException(
                    "임금은 1원 이상 100000000원 이하여야 합니다."
            );
        }
        if (wageUnit == null) {
            throw new IllegalArgumentException("임금 단위는 필수입니다.");
        }
        supplies = optionalText(supplies, "준비물", 1000);
        precautions = optionalText(precautions, "주의사항", 2000);
        farmMessage = optionalText(farmMessage, "농가 메시지", 1000);
        applicantPreference = optionalText(
                applicantPreference,
                "희망 지원자 조건",
                1000
        );
        title = requiredText(title, "공고 제목", 150);
        description = requiredText(description, "공고 설명", 5000);
        beginnerGuide = optionalText(beginnerGuide, "초보자 안내", 2000);
    }

    private static String requiredText(
            String value,
            String fieldName,
            int maxLength
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "은(는) 필수입니다.");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + "은(는) " + maxLength + "자 이하여야 합니다."
            );
        }
        return normalized;
    }

    private static String optionalText(
            String value,
            String fieldName,
            int maxLength
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + "은(는) " + maxLength + "자 이하여야 합니다."
            );
        }
        return normalized;
    }
}
