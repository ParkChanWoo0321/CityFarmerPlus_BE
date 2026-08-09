package chungbuk.cityfarmerplus.urbanfarmer.service;

import chungbuk.cityfarmerplus.urbanfarmer.entity.UrbanFarmerProfile;
import chungbuk.cityfarmerplus.urbanfarmer.repository.UrbanFarmerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UrbanFarmerEducationService {

    private final UrbanFarmerProfileRepository urbanFarmerProfileRepository;

    public UrbanFarmerProfile getStatus(Long userId) {
        return findProfile(userId);
    }

    public UrbanFarmerProfile registerCertificate(Long userId) {
        UrbanFarmerProfile profile = findProfile(userId);
        profile.setEducationStatus(UrbanFarmerProfile.EducationStatus.CERTIFICATE_REGISTERED);
        return urbanFarmerProfileRepository.save(profile);
    }

    // 관리자 승인(admin.controller.AdminUrbanFarmerController)을 통해서만 호출된다.
    public UrbanFarmerProfile completeEducation(Long userId) {
        UrbanFarmerProfile profile = findProfile(userId);
        profile.setEducationStatus(UrbanFarmerProfile.EducationStatus.COMPLETED);
        return urbanFarmerProfileRepository.save(profile);
    }

    private UrbanFarmerProfile findProfile(Long userId) {
        return urbanFarmerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("먼저 프로필을 등록해주세요."));
    }
}
