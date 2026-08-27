package chungbuk.cityfarmerplus.admin.proxy.dto;

import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

public record ProxyWorkPreferenceRequest(
        @NotEmpty(message = "희망 근무 지역을 하나 이상 선택해야 합니다.")
        @Size(max = 11, message = "희망 근무 지역은 11개 이하여야 합니다.")
        List<@NotNull ChungbukCityCounty> preferredRegions,

        @NotEmpty(message = "희망 근무 요일을 하나 이상 선택해야 합니다.")
        @Size(max = 7, message = "희망 근무 요일은 7개 이하여야 합니다.")
        List<@NotNull DayOfWeek> availableDays,

        @NotEmpty(message = "가능한 작업 유형을 하나 이상 입력해야 합니다.")
        @Size(max = 20, message = "가능한 작업 유형은 20개 이하여야 합니다.")
        List<@NotBlank @Size(max = 50)
                @Pattern(
                        regexp = "^[^,\\r\\n]+$",
                        message = "작업 유형에는 쉼표나 줄바꿈을 사용할 수 없습니다."
                ) String> availableWorkTypes,

        @NotNull(message = "희망 근무 시작일은 필수입니다.")
        LocalDate preferredStartDate,

        @NotNull(message = "희망 근무 종료일은 필수입니다.")
        LocalDate preferredEndDate,

        @NotNull(message = "이동 가능 여부를 입력해야 합니다.")
        Boolean canTravel,

        @Size(max = 1000, message = "특이사항은 1000자 이하여야 합니다.")
        String notes,

        @NotBlank(message = "대리 등록 사유를 입력해야 합니다.")
        @Size(max = 1000, message = "대리 등록 사유는 1000자 이하여야 합니다.")
        String reason
) {

    @AssertTrue(message = "희망 근무 종료일은 시작일보다 빠를 수 없습니다.")
    public boolean isPreferredPeriodValid() {
        return preferredStartDate == null
                || preferredEndDate == null
                || !preferredEndDate.isBefore(preferredStartDate);
    }
}
