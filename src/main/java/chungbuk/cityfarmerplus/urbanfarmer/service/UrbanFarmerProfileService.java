package chungbuk.cityfarmerplus.urbanfarmer.service;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import chungbuk.cityfarmerplus.urbanfarmer.dto.UrbanFarmerProfileRequest;
import chungbuk.cityfarmerplus.urbanfarmer.entity.UrbanFarmerProfile;
import chungbuk.cityfarmerplus.urbanfarmer.repository.UrbanFarmerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UrbanFarmerProfileService {

    private final UrbanFarmerProfileRepository urbanFarmerProfileRepository;
    private final UserRepository userRepository;

    public UrbanFarmerProfile register(Long userId, UrbanFarmerProfileRequest request) {
        if (urbanFarmerProfileRepository.existsByUserId(userId)) {
            throw new IllegalArgumentException("이미 등록된 도시농부 프로필이 있습니다.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        UrbanFarmerProfile profile = UrbanFarmerProfile.builder()
                .user(user)
                .preferredRegion(request.getPreferredRegion())
                .preferredDays(request.getPreferredDays())
                .preferredWorkType(request.getPreferredWorkType())
                .canTravel(request.isCanTravel())
                .experienceCount(request.getExperienceCount())
                .introduction(request.getIntroduction())
                .build();

        return urbanFarmerProfileRepository.save(profile);
    }

    public UrbanFarmerProfile getMyProfile(Long userId) {
        return urbanFarmerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("등록된 도시농부 프로필이 없습니다."));
    }

    public UrbanFarmerProfile updateMyProfile(Long userId, UrbanFarmerProfileRequest request) {
        UrbanFarmerProfile profile = getMyProfile(userId);

        profile.setPreferredRegion(request.getPreferredRegion());
        profile.setPreferredDays(request.getPreferredDays());
        profile.setPreferredWorkType(request.getPreferredWorkType());
        profile.setCanTravel(request.isCanTravel());
        profile.setExperienceCount(request.getExperienceCount());
        profile.setIntroduction(request.getIntroduction());

        return urbanFarmerProfileRepository.save(profile);
    }
}
