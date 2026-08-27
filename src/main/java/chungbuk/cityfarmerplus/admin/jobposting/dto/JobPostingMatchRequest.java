package chungbuk.cityfarmerplus.admin.jobposting.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record JobPostingMatchRequest(
        @NotEmpty(message = "매칭할 지원 ID 목록을 입력해야 합니다.")
        @Size(max = 100, message = "한 번에 최대 100건까지 매칭할 수 있습니다.")
        List<@NotNull(message = "지원 ID는 필수입니다.")
                @Positive(message = "지원 ID는 양수여야 합니다.") Long> applicationIds
) {
}
