package chungbuk.cityfarmerplus.urbanfarmer.profile.service;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.common.exception.DomainException;
import chungbuk.cityfarmerplus.urbanfarmer.profile.dto.UrbanFarmerProfileRequest;
import chungbuk.cityfarmerplus.urbanfarmer.profile.dto.UrbanFarmerProfileResponse;
import chungbuk.cityfarmerplus.urbanfarmer.profile.entity.UrbanFarmerProfile;
import chungbuk.cityfarmerplus.urbanfarmer.profile.repository.UrbanFarmerProfileRepository;
import chungbuk.cityfarmerplus.urbanfarmer.service.UserRoleAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UrbanFarmerProfileService {

    private final UrbanFarmerProfileRepository profileRepository;
    private final UserRoleAccessService accessService;

    @Transactional
    public UrbanFarmerProfileResponse create(
            Long userId,
            UrbanFarmerProfileRequest request
    ) {
        User user = accessService.requireUrbanFarmerForUpdate(userId);
        if (profileRepository.existsByUrbanFarmerId(userId)) {
            throw error(
                    HttpStatus.CONFLICT,
                    "URBAN_FARMER_PROFILE_ALREADY_EXISTS",
                    "이미 도시농부 프로필이 등록되어 있습니다."
            );
        }
        UrbanFarmerProfile profile = UrbanFarmerProfile.create(
                user,
                request.agriculturalBusinessRegistered(),
                request.experienceCount(),
                normalizeNullable(request.notes())
        );
        return UrbanFarmerProfileResponse.from(profileRepository.saveAndFlush(profile));
    }

    @Transactional(readOnly = true)
    public UrbanFarmerProfileResponse getMine(Long userId) {
        accessService.requireUrbanFarmer(userId);
        return UrbanFarmerProfileResponse.from(getProfile(userId));
    }

    @Transactional
    public UrbanFarmerProfileResponse update(
            Long userId,
            UrbanFarmerProfileRequest request
    ) {
        accessService.requireUrbanFarmerForUpdate(userId);
        UrbanFarmerProfile profile = getProfile(userId);
        profile.update(
                request.agriculturalBusinessRegistered(),
                request.experienceCount(),
                normalizeNullable(request.notes())
        );
        return UrbanFarmerProfileResponse.from(profile);
    }

    private UrbanFarmerProfile getProfile(Long userId) {
        return profileRepository.findByUrbanFarmerId(userId)
                .orElseThrow(() -> error(
                        HttpStatus.NOT_FOUND,
                        "URBAN_FARMER_PROFILE_NOT_FOUND",
                        "도시농부 프로필을 찾을 수 없습니다."
                ));
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private DomainException error(HttpStatus status, String code, String message) {
        return new DomainException(status, code, message);
    }
}
