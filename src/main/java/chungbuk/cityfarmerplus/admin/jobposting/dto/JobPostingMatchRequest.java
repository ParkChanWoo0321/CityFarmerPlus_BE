package chungbuk.cityfarmerplus.admin.jobposting.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record JobPostingMatchRequest(
        @NotEmpty(message = "매칭할 지원 ID 목록을 입력해야 합니다.")
        List<Long> applicationIds
) {
}
