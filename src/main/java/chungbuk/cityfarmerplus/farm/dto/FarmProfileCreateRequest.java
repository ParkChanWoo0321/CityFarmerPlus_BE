package chungbuk.cityfarmerplus.farm.dto;

import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record FarmProfileCreateRequest(
        @NotBlank(message = "농가명은 필수입니다.")
        @Size(max = 100, message = "농가명은 100자 이하여야 합니다.")
        String farmName,

        @NotBlank(message = "대표자명은 필수입니다.")
        @Size(max = 50, message = "대표자명은 50자 이하여야 합니다.")
        String representativeName,

        @NotBlank(message = "연락처는 필수입니다.")
        @Pattern(
                regexp = "^0\\d{1,2}-?\\d{3,4}-?\\d{4}$",
                message = "연락처 형식이 올바르지 않습니다."
        )
        String contactNumber,

        @NotBlank(message = "농가 주소는 필수입니다.")
        @Size(max = 255, message = "농가 주소는 255자 이하여야 합니다.")
        String farmAddress,

        @NotNull(message = "시·군은 필수입니다.")
        ChungbukCityCounty cityCounty,

        @NotEmpty(message = "재배 작물을 한 개 이상 입력해야 합니다.")
        @Size(max = 20, message = "재배 작물은 최대 20개까지 입력할 수 있습니다.")
        List<
                @NotBlank(message = "재배 작물은 비어 있을 수 없습니다.")
                @Size(max = 50, message = "재배 작물명은 50자 이하여야 합니다.")
                String> crops,

        @NotBlank(message = "주요 활동 내용은 필수입니다.")
        @Size(max = 2000, message = "주요 활동 내용은 2000자 이하여야 합니다.")
        String mainActivities,

        @Pattern(
                regexp = "^$|^\\d{3}-?\\d{2}-?\\d{5}$",
                message = "사업자 번호 형식이 올바르지 않습니다."
        )
        String businessRegistrationNumber
) {
}
