package chungbuk.cityfarmerplus.jobposting.dto;

import jakarta.validation.constraints.Size;

public record ApplicantPreferenceRequest(
        @Size(max = 1000, message = "희망 지원자 조건은 1000자 이하여야 합니다.")
        String applicantPreference
) {
}
