package chungbuk.cityfarmerplus.urbanfarmer.dto;

import chungbuk.cityfarmerplus.urbanfarmer.entity.UrbanFarmerProfile;
import lombok.Getter;

@Getter
public class EducationStatusResponse {

    private final UrbanFarmerProfile.EducationStatus educationStatus;

    public EducationStatusResponse(UrbanFarmerProfile profile) {
        this.educationStatus = profile.getEducationStatus();
    }
}
