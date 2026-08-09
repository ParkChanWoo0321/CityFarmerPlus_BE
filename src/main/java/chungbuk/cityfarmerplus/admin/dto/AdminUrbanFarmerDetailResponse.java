package chungbuk.cityfarmerplus.admin.dto;

import chungbuk.cityfarmerplus.urbanfarmer.entity.UrbanFarmerProfile;
import lombok.Getter;

@Getter
public class AdminUrbanFarmerDetailResponse {

    private final Long userId;
    private final String loginId;
    private final String name;
    private final String preferredRegion;
    private final String preferredDays;
    private final String preferredWorkType;
    private final boolean canTravel;
    private final int experienceCount;
    private final String introduction;
    private final boolean farmBusinessRegistered;
    private final boolean eligibilityVerified;
    private final UrbanFarmerProfile.EducationStatus educationStatus;

    public AdminUrbanFarmerDetailResponse(UrbanFarmerProfile profile) {
        this.userId = profile.getUser().getId();
        this.loginId = profile.getUser().getLoginId();
        this.name = profile.getUser().getName();
        this.preferredRegion = profile.getPreferredRegion();
        this.preferredDays = profile.getPreferredDays();
        this.preferredWorkType = profile.getPreferredWorkType();
        this.canTravel = profile.isCanTravel();
        this.experienceCount = profile.getExperienceCount();
        this.introduction = profile.getIntroduction();
        this.farmBusinessRegistered = profile.isFarmBusinessRegistered();
        this.eligibilityVerified = profile.isEligibilityVerified();
        this.educationStatus = profile.getEducationStatus();
    }
}
