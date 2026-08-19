package chungbuk.cityfarmerplus.work.guide;

import chungbuk.cityfarmerplus.work.entity.WorkAssignment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class RuleBasedWorkGuideGenerator implements WorkGuideGenerator {

    @Override
    public WorkGuideResponse generate(WorkAssignment assignment) {
        List<String> clothing = new ArrayList<>(List.of("편한 긴소매 작업복", "모자", "미끄럼 방지 작업화"));
        List<String> safety = new ArrayList<>(List.of(
                "작업 전 스트레칭을 해주세요.",
                "물을 자주 마시고 무리하지 마세요.",
                "도구 사용 전 농가의 안전 설명을 들어주세요."
        ));
        List<String> steps = new ArrayList<>(List.of(
                "집결 장소에서 출석과 작업 구역을 확인합니다.",
                "농가의 시범과 안전 설명을 듣습니다.",
                "안내받은 구역에서 천천히 작업합니다.",
                "수확물이나 도구를 지정된 장소에 정리합니다."
        ));
        if (assignment.getWorkType().contains("방제")) {
            clothing.add("보호 마스크와 보호 장갑");
            safety.add("약제에 직접 닿지 않도록 보호장구를 끝까지 착용하세요.");
        }
        if (assignment.getWorkType().contains("수확")) {
            steps.add("작물에 상처가 나지 않도록 양손으로 조심스럽게 수확합니다.");
        }
        List<String> checklist = preparationChecklist(assignment.getSupplies());
        return new WorkGuideResponse(
                assignment.getId(),
                assignment.getCrop() + " " + assignment.getWorkType() + " 작업입니다. "
                        + assignment.getStartTime() + "까지 " + assignment.getMeetingPlace() + "에 모여주세요.",
                trimToNull(assignment.getPrecautions()),
                checklist,
                clothing,
                safety,
                steps,
                "처음이라도 괜찮습니다. 모르는 작업은 임의로 진행하지 말고 농가에 바로 물어보세요.",
                "RULE_BASED_V1"
        );
    }

    private List<String> preparationChecklist(String supplies) {
        LinkedHashSet<String> items = new LinkedHashSet<>(
                List.of("작업 장갑", "물", "개인 상비약")
        );
        if (supplies != null && !supplies.isBlank()) {
            for (String value : supplies.split("[,\\r\\n]+")) {
                String normalized = value.trim();
                if (!normalized.isEmpty()) {
                    items.add(normalized);
                }
            }
        }
        return List.copyOf(items);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
