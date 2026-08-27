package chungbuk.cityfarmerplus.admin.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EducationCourseRequest(
        @NotBlank(message = "과정명은 필수입니다.")
        @Size(max = 150, message = "과정명은 150자 이하여야 합니다.")
        String title,

        @NotBlank(message = "과정 설명은 필수입니다.")
        @Size(max = 2000, message = "과정 설명은 2000자 이하여야 합니다.")
        String description,

        int requiredHours,

        @Size(max = 500, message = "외부 신청 URL은 500자 이하여야 합니다.")
        String externalApplicationUrl,

        boolean mandatory,

        int displayOrder
) {
}
