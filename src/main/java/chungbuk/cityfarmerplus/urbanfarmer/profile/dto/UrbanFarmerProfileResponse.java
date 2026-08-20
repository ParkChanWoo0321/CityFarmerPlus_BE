package chungbuk.cityfarmerplus.urbanfarmer.profile.dto;

import chungbuk.cityfarmerplus.urbanfarmer.profile.entity.UrbanFarmerProfile;

import java.time.Instant;

public record UrbanFarmerProfileResponse(
        Long id,
        Long userId,
        boolean agriculturalBusinessRegistered,
        int experienceCount,
        String notes,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public static UrbanFarmerProfileResponse from(UrbanFarmerProfile profile) {
        return new UrbanFarmerProfileResponse(
                profile.getId(),
                profile.getUrbanFarmer().getId(),
                profile.isAgriculturalBusinessRegistered(),
                profile.getExperienceCount(),
                profile.getNotes(),
                profile.getVersion(),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }
}
