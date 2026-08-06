package chungbuk.cityfarmerplus.urbanfarmer.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class UrbanFarmerProfileRequest {

    @NotBlank(message = "희망 근무 지역은 필수입니다.")
    private String preferredRegion;

    @NotBlank(message = "희망 근무 가능 요일은 필수입니다.")
    private String preferredDays;

    @NotBlank(message = "선호 작업 종류는 필수입니다.")
    private String preferredWorkType;

    private boolean canTravel;

    @Min(value = 0, message = "농작업 경험 횟수는 0 이상이어야 합니다.")
    private int experienceCount;

    @Size(max = 500, message = "자기소개는 500자를 초과할 수 없습니다.")
    private String introduction;
}
