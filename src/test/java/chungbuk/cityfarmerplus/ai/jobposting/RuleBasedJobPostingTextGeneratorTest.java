package chungbuk.cityfarmerplus.ai.jobposting;

import chungbuk.cityfarmerplus.jobposting.dto.JobPostingTextLimits;
import chungbuk.cityfarmerplus.jobposting.dto.JobPostingUpsertRequest;
import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedJobPostingTextGeneratorTest {

    private final RuleBasedJobPostingTextGenerator generator =
            new RuleBasedJobPostingTextGenerator();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void generatesEditableSafePreviewWithoutPublishingAnything() {
        AiJobPostingPreviewResponse response = generator.generate(
                new AiJobPostingPreviewRequest(
                        "감자",
                        "수확",
                        LocalDate.of(2026, 8, 20),
                        LocalTime.of(9, 0),
                        LocalTime.of(16, 0),
                        3,
                        "농장 입구",
                        null,
                        null
                )
        );

        assertThat(response.title()).contains("감자", "수확");
        assertThat(response.supplies()).contains("작업 장갑");
        assertThat(response.precautions()).contains("안전거리");
        assertThat(response.generator()).isEqualTo("RULE_BASED_V1");
    }

    @Test
    void keepsBoundaryPreviewWithinCreateBeanValidationLimitsWithoutBreakingCodePoints() {
        AiJobPostingPreviewRequest previewRequest = new AiJobPostingPreviewRequest(
                "🌽".repeat(25),
                "🚜".repeat(50),
                LocalDate.now().plusDays(1),
                LocalTime.of(9, 0),
                LocalTime.of(16, 0),
                1000,
                "농장 입구",
                "🧤".repeat(500),
                "⚠️".repeat(1000)
        );

        assertThat(validator.validate(previewRequest)).isEmpty();

        AiJobPostingPreviewResponse response = generator.generate(previewRequest);

        assertThat(response.title()).hasSizeLessThanOrEqualTo(JobPostingTextLimits.TITLE_MAX_LENGTH);
        assertThat(response.description()).hasSizeLessThanOrEqualTo(JobPostingTextLimits.DESCRIPTION_MAX_LENGTH);
        assertThat(response.supplies()).hasSizeLessThanOrEqualTo(JobPostingTextLimits.SUPPLIES_MAX_LENGTH);
        assertThat(response.precautions()).hasSizeLessThanOrEqualTo(JobPostingTextLimits.PRECAUTIONS_MAX_LENGTH);
        assertThat(response.beginnerGuide()).hasSizeLessThanOrEqualTo(JobPostingTextLimits.BEGINNER_GUIDE_MAX_LENGTH);
        assertCodePointSafe(response.title());
        assertCodePointSafe(response.description());
        assertCodePointSafe(response.supplies());
        assertCodePointSafe(response.precautions());
        assertCodePointSafe(response.beginnerGuide());

        JobPostingUpsertRequest createRequest = new JobPostingUpsertRequest(
                previewRequest.crop(),
                previewRequest.workType(),
                previewRequest.workDate(),
                previewRequest.startTime(),
                previewRequest.endTime(),
                previewRequest.capacity(),
                previewRequest.meetingPlace(),
                100_000,
                JobPosting.WageUnit.DAILY,
                response.supplies(),
                response.precautions(),
                null,
                null,
                response.title(),
                response.description(),
                response.beginnerGuide()
        );

        assertThat(validator.validate(createRequest)).isEmpty();
    }

    private void assertCodePointSafe(String value) {
        assertThat(value.codePoints()
                .noneMatch(codePoint -> codePoint >= Character.MIN_SURROGATE
                        && codePoint <= Character.MAX_SURROGATE))
                .isTrue();
    }
}
