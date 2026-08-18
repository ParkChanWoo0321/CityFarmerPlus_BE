package chungbuk.cityfarmerplus.application.dto;

import chungbuk.cityfarmerplus.application.entity.JobApplication;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FarmOpinionRequest(
        @NotNull(message = "농가 의견은 필수입니다.")
        JobApplication.FarmOpinion opinion,
        @Size(max = 1000, message = "농가 의견 메모는 1000자 이하여야 합니다.")
        String note
) {
}
