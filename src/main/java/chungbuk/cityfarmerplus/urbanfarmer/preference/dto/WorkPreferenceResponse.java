package chungbuk.cityfarmerplus.urbanfarmer.preference.dto;

import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import chungbuk.cityfarmerplus.urbanfarmer.preference.entity.UrbanFarmerWorkPreference;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record WorkPreferenceResponse(
        Long id,
        Long urbanFarmerId,
        List<ChungbukCityCounty> preferredRegions,
        List<DayOfWeek> availableDays,
        List<String> availableWorkTypes,
        LocalDate preferredStartDate,
        LocalDate preferredEndDate,
        boolean canTravel,
        String notes,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public static WorkPreferenceResponse from(UrbanFarmerWorkPreference preference) {
        return new WorkPreferenceResponse(
                preference.getId(),
                preference.getUrbanFarmer().getId(),
                List.copyOf(preference.getPreferredRegions()),
                List.copyOf(preference.getAvailableDays()),
                List.copyOf(preference.getAvailableWorkTypes()),
                preference.getPreferredStartDate(),
                preference.getPreferredEndDate(),
                preference.isCanTravel(),
                preference.getNotes(),
                preference.getVersion(),
                preference.getCreatedAt(),
                preference.getUpdatedAt()
        );
    }
}
