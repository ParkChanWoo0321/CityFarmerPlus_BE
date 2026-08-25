package chungbuk.cityfarmerplus.ai.jobposting;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record AiJobPostingPreviewRequest(
        @NotBlank @Size(max = 50) String crop,
        @NotBlank @Size(max = 100) String workType,
        @NotNull @FutureOrPresent LocalDate workDate,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @Min(1) @Max(1000) int capacity,
        @NotBlank @Size(max = 255) String meetingPlace,
        @Size(max = 1000) String supplies,
        @Size(max = 2000) String precautions
) {
}
