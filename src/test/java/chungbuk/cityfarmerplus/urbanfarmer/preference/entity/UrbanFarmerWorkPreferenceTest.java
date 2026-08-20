package chungbuk.cityfarmerplus.urbanfarmer.preference.entity;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrbanFarmerWorkPreferenceTest {

    @Test
    void createsPreferenceWithInclusiveDatePeriod() {
        LocalDate workDate = LocalDate.of(2026, 9, 1);

        UrbanFarmerWorkPreference preference = UrbanFarmerWorkPreference.create(
                urbanFarmer(),
                List.of(ChungbukCityCounty.CHUNGJU),
                List.of(DayOfWeek.MONDAY),
                List.of("수확 보조"),
                workDate,
                workDate,
                true,
                null
        );

        assertThat(preference.getPreferredStartDate()).isEqualTo(workDate);
        assertThat(preference.getPreferredEndDate()).isEqualTo(workDate);
    }

    @Test
    void rejectsEndDateBeforeStartDate() {
        assertThatThrownBy(() -> UrbanFarmerWorkPreference.create(
                urbanFarmer(),
                List.of(ChungbukCityCounty.CHUNGJU),
                List.of(DayOfWeek.MONDAY),
                List.of("수확 보조"),
                LocalDate.of(2026, 9, 2),
                LocalDate.of(2026, 9, 1),
                true,
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("종료일");
    }

    @Test
    void rejectsInvalidWorkTypesAtDomainBoundary() {
        assertInvalidWorkType(null);
        assertInvalidWorkType("");
        assertInvalidWorkType("   ");
        assertInvalidWorkType("a".repeat(51));
        assertInvalidWorkType("수확,선별");
        assertInvalidWorkType("수확\r선별");
        assertInvalidWorkType("수확\n선별");
    }

    private void assertInvalidWorkType(String workType) {
        assertThatThrownBy(() -> UrbanFarmerWorkPreference.create(
                urbanFarmer(),
                List.of(ChungbukCityCounty.CHUNGJU),
                List.of(DayOfWeek.MONDAY),
                java.util.Collections.singletonList(workType),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
                true,
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private User urbanFarmer() {
        return User.register(
                "urban_preference_test",
                "encoded-password",
                "도시농부",
                User.UserType.URBAN_FARMER
        );
    }
}
