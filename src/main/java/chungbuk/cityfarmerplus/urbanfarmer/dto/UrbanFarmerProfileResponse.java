package chungbuk.cityfarmerplus.urbanfarmer.dto;

import chungbuk.cityfarmerplus.urbanfarmer.entity.UrbanFarmerProfile;
import lombok.Getter;

@Getter
public class UrbanFarmerProfileResponse {

    private final Long id;
    private final Long userId;
    private final String preferredRegion;
    private final String preferredDays;
    private final String preferredWorkType;
    private final boolean canTravel;
    private final int experienceCount;
    private final String introduction;

    public UrbanFarmerProfileResponse(UrbanFarmerProfile profile) {
        this.id = profile.getId();
        this.userId = profile.getUser().getId();
        this.preferredRegion = profile.getPreferredRegion();
        this.preferredDays = profile.getPreferredDays();
        this.preferredWorkType = profile.getPreferredWorkType();
        this.canTravel = profile.isCanTravel();
        this.experienceCount = profile.getExperienceCount();
        this.introduction = profile.getIntroduction();
    }
}
