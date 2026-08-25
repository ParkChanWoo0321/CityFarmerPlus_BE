package chungbuk.cityfarmerplus.ai.jobposting;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static chungbuk.cityfarmerplus.jobposting.dto.JobPostingTextLimits.BEGINNER_GUIDE_MAX_LENGTH;
import static chungbuk.cityfarmerplus.jobposting.dto.JobPostingTextLimits.DESCRIPTION_MAX_LENGTH;
import static chungbuk.cityfarmerplus.jobposting.dto.JobPostingTextLimits.PRECAUTIONS_MAX_LENGTH;
import static chungbuk.cityfarmerplus.jobposting.dto.JobPostingTextLimits.SUPPLIES_MAX_LENGTH;
import static chungbuk.cityfarmerplus.jobposting.dto.JobPostingTextLimits.TITLE_MAX_LENGTH;

@Component
public class RuleBasedJobPostingTextGenerator implements JobPostingTextGenerator {

    @Override
    public AiJobPostingPreviewResponse generate(AiJobPostingPreviewRequest request) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw new IllegalArgumentException("종료 시간은 시작 시간보다 늦어야 합니다.");
        }

        String crop = request.crop().trim();
        String workType = request.workType().trim();
        String title = appendRequiredSuffix(
                crop + " " + workType,
                " 작업자를 모집합니다",
                TITLE_MAX_LENGTH
        );
        String description = truncateSafely("%s에 %s 작업을 함께해 주실 도시농부 %d명을 모집합니다. 작업은 %s부터 %s까지 진행되며, 처음 참여하시는 분도 안내를 받으며 작업할 수 있습니다."
                .formatted(
                        request.workDate(),
                        workType,
                        request.capacity(),
                        request.startTime(),
                        request.endTime()
                ), DESCRIPTION_MAX_LENGTH);
        String supplies = merge(request.supplies(), inferSupplies(workType), SUPPLIES_MAX_LENGTH);
        String precautions = merge(request.precautions(), inferPrecautions(workType), PRECAUTIONS_MAX_LENGTH);
        String beginnerGuide = truncateSafely(
                "집결 장소에서 농가의 작업 설명을 먼저 듣고, 무리하지 않는 범위에서 천천히 작업해 주세요. 모르는 부분은 바로 농가에 확인해 주세요.",
                BEGINNER_GUIDE_MAX_LENGTH
        );

        return new AiJobPostingPreviewResponse(
                title,
                description,
                supplies,
                precautions,
                beginnerGuide,
                "RULE_BASED_V1"
        );
    }

    private String inferSupplies(String workType) {
        String normalized = workType.toLowerCase(Locale.ROOT);
        List<String> items = new ArrayList<>(List.of("작업 장갑", "모자", "편한 작업복"));
        if (normalized.contains("수확") || normalized.contains("선별")) {
            items.add("미끄럼 방지 작업화");
        }
        if (normalized.contains("제초") || normalized.contains("방제")) {
            items.add("팔토시");
            items.add("마스크");
        }
        return String.join(", ", items);
    }

    private String inferPrecautions(String workType) {
        String base = "작업 전 스트레칭을 하고, 충분히 물을 마시며 무리하지 마세요.";
        if (workType.contains("방제")) {
            return base + " 방제 작업 중에는 보호장구를 착용하고 농가의 안전 지시를 반드시 따라 주세요.";
        }
        return base + " 작업 도구를 사용할 때 주변 사람과 안전거리를 유지해 주세요.";
    }

    private String merge(String userInput, String suggestion, int maxLength) {
        if (userInput == null || userInput.isBlank()) {
            return truncateSafely(suggestion, maxLength);
        }

        String boundedInput = truncateSafely(userInput.trim(), maxLength);
        String addition = "\n" + suggestion;
        if (boundedInput.length() + addition.length() <= maxLength) {
            return boundedInput + addition;
        }
        return boundedInput;
    }

    private String appendRequiredSuffix(String value, String suffix, int maxLength) {
        if (value.length() + suffix.length() <= maxLength) {
            return value + suffix;
        }

        int valueLimit = Math.max(0, maxLength - suffix.length());
        return truncateSafely(value, valueLimit).stripTrailing() + suffix;
    }

    private String truncateSafely(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }

        int endIndex = maxLength;
        if (endIndex > 0
                && Character.isHighSurrogate(value.charAt(endIndex - 1))
                && Character.isLowSurrogate(value.charAt(endIndex))) {
            endIndex--;
        }
        return value.substring(0, endIndex);
    }
}
