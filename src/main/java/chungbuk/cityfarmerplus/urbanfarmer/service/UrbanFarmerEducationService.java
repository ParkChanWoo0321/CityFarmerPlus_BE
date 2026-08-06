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

    // 지금은 승인 절차 없이 바로 완료 처리. 추후 중개센터 관리자 기능 추가 시 승인 절차를 거치도록 변경 예정.
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
