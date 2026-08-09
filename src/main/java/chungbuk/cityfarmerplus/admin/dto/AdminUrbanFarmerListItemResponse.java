package chungbuk.cityfarmerplus.admin.dto;

import chungbuk.cityfarmerplus.urbanfarmer.entity.UrbanFarmerProfile;
import lombok.Getter;

@Getter
public class AdminUrbanFarmerListItemResponse {

    private final Long userId;
    private final String name;
    private final String preferredRegion;
    private final UrbanFarmerProfile.EducationStatus educationStatus;
    private final boolean eligibilityVerified;

    public AdminUrbanFarmerListItemResponse(UrbanFarmerProfile profile) {
        this.userId = profile.getUser().getId();
        this.name = profile.getUser().getName();
        this.preferredRegion = profile.getPreferredRegion();
        this.educationStatus = profile.getEducationStatus();
        this.eligibilityVerified = profile.isEligibilityVerified();
    }
}
