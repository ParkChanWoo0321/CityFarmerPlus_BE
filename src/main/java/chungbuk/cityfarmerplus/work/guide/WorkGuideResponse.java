package chungbuk.cityfarmerplus.work.guide;

import java.util.List;

public record WorkGuideResponse(
        Long workAssignmentId,
        String workSummary,
        String officialPrecautions,
        List<String> preparationChecklist,
        List<String> recommendedClothing,
        List<String> safetyRules,
        List<String> workSteps,
        String beginnerTip,
        String generator
) {
}
