package chungbuk.cityfarmerplus.admin.service;

import chungbuk.cityfarmerplus.admin.dto.AdminUrbanFarmerDetailResponse;
import chungbuk.cityfarmerplus.admin.dto.AdminUrbanFarmerListItemResponse;
import chungbuk.cityfarmerplus.urbanfarmer.dto.UrbanFarmerProfileRequest;
import chungbuk.cityfarmerplus.urbanfarmer.dto.UrbanFarmerProfileResponse;
import chungbuk.cityfarmerplus.urbanfarmer.entity.UrbanFarmerProfile;
import chungbuk.cityfarmerplus.urbanfarmer.repository.UrbanFarmerProfileRepository;
import chungbuk.cityfarmerplus.urbanfarmer.service.UrbanFarmerEducationService;
import chungbuk.cityfarmerplus.urbanfarmer.service.UrbanFarmerProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminUrbanFarmerService {

    private final UrbanFarmerProfileRepository urbanFarmerProfileRepository;
    private final UrbanFarmerProfileService urbanFarmerProfileService;
    private final UrbanFarmerEducationService urbanFarmerEducationService;

    public Page<AdminUrbanFarmerListItemResponse> list(Pageable pageable) {
        return urbanFarmerProfileRepository.findAll(pageable)
                .map(AdminUrbanFarmerListItemResponse::new);
    }

    public AdminUrbanFarmerDetailResponse getDetail(Long userId) {
        return new AdminUrbanFarmerDetailResponse(findProfile(userId));
    }

    public AdminUrbanFarmerDetailResponse verifyEligibility(Long userId) {
        urbanFarmerProfileService.verifyEligibility(userId);
        return new AdminUrbanFarmerDetailResponse(findProfile(userId));
    }

    public AdminUrbanFarmerDetailResponse approveEducation(Long userId) {
        urbanFarmerEducationService.completeEducation(userId);
        return new AdminUrbanFarmerDetailResponse(findProfile(userId));
    }

    public UrbanFarmerProfileResponse registerProfile(Long userId, UrbanFarmerProfileRequest request) {
        UrbanFarmerProfile profile = urbanFarmerProfileService.register(userId, request);
        return new UrbanFarmerProfileResponse(profile);
    }

    private UrbanFarmerProfile findProfile(Long userId) {
        return urbanFarmerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("등록된 도시농부 프로필이 없습니다."));
    }
}
