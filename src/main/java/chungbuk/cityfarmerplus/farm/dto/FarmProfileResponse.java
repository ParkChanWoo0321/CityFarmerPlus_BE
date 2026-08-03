package chungbuk.cityfarmerplus.farm.dto;

import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import chungbuk.cityfarmerplus.farm.entity.FarmProfile;

import java.time.Instant;
import java.util.List;

public record FarmProfileResponse(
        Long id,
        String farmName,
        String representativeName,
        String contactNumber,
        String farmAddress,
        ChungbukCityCounty cityCounty,
        List<String> crops,
        String mainActivities,
        String businessRegistrationNumber,
        FarmProfile.FarmProfileStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public static FarmProfileResponse from(FarmProfile profile) {
        return new FarmProfileResponse(
                profile.getId(),
                profile.getFarmName(),
                profile.getRepresentativeName(),
                profile.getContactNumber(),
                profile.getFarmAddress(),
                profile.getCityCounty(),
                List.copyOf(profile.getCrops()),
                profile.getMainActivities(),
                profile.getBusinessRegistrationNumber(),
                profile.getStatus(),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }
}
