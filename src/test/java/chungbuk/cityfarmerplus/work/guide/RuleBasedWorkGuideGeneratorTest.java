package chungbuk.cityfarmerplus.work.guide;

import chungbuk.cityfarmerplus.work.entity.WorkAssignment;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuleBasedWorkGuideGeneratorTest {

    private final RuleBasedWorkGuideGenerator generator =
            new RuleBasedWorkGuideGenerator();

    @Test
    void splitsSuppliesRemovesDuplicatesAndExposesFarmPrecautions() {
        WorkAssignment assignment = assignment();
        when(assignment.getSupplies())
                .thenReturn("작업 장갑, 모자\r\n장화, 모자");
        when(assignment.getPrecautions())
                .thenReturn("농기계 주변에서는 안전거리를 유지해 주세요. ");

        WorkGuideResponse response = generator.generate(assignment);

        assertThat(response.preparationChecklist()).containsExactly(
                "작업 장갑",
                "물",
                "개인 상비약",
                "모자",
                "장화"
        );
        assertThat(response.officialPrecautions())
                .isEqualTo("농기계 주변에서는 안전거리를 유지해 주세요.");
    }

    @Test
    void keepsDefaultChecklistAndNullPrecautionsWhenFarmInputIsBlank() {
        WorkAssignment assignment = assignment();
        when(assignment.getSupplies()).thenReturn("  ");
        when(assignment.getPrecautions()).thenReturn(null);

        WorkGuideResponse response = generator.generate(assignment);

        assertThat(response.preparationChecklist()).containsExactly(
                "작업 장갑",
                "물",
                "개인 상비약"
        );
        assertThat(response.officialPrecautions()).isNull();
    }

    private WorkAssignment assignment() {
        WorkAssignment assignment = mock(WorkAssignment.class);
        when(assignment.getId()).thenReturn(10L);
        when(assignment.getCrop()).thenReturn("감자");
        when(assignment.getWorkType()).thenReturn("수확");
        when(assignment.getStartTime()).thenReturn(LocalTime.of(9, 0));
        when(assignment.getMeetingPlace()).thenReturn("농장 입구");
        return assignment;
    }
}
