package chungbuk.cityfarmerplus.ai.support;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupportMessageRequest(
        @NotBlank(message = "문의 내용은 필수입니다.")
        @Size(max = 1000, message = "문의 내용은 1000자 이하여야 합니다.")
        String message
) {
}
