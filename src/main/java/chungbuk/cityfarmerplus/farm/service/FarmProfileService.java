package chungbuk.cityfarmerplus.farm.service;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.exception.AuthException;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import chungbuk.cityfarmerplus.farm.dto.FarmProfileCreateRequest;
import chungbuk.cityfarmerplus.farm.dto.FarmProfileResponse;
import chungbuk.cityfarmerplus.farm.dto.FarmProfileUpdateRequest;
import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import chungbuk.cityfarmerplus.farm.exception.FarmProfileException;
import chungbuk.cityfarmerplus.farm.repository.FarmProfileRepository;
import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;
import chungbuk.cityfarmerplus.jobposting.repository.JobPostingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FarmProfileService {

    private final FarmProfileRepository farmProfileRepository;
    private final UserRepository userRepository;
    private final JobPostingRepository jobPostingRepository;

    @Transactional
    public FarmProfileResponse create(Long userId, FarmProfileCreateRequest request) {
        User owner = getActiveFarmOwnerForUpdate(userId);

        if (farmProfileRepository.existsByOwnerId(userId)) {
            throw FarmProfileException.profileAlreadyExists();
        }

        String businessNumber = normalizeOptionalNumber(
                request.businessRegistrationNumber()
        );

        FarmProfile profile = FarmProfile.createDraft(
                owner,
                request.farmName().trim(),
                request.representativeName().trim(),
                normalizeRequiredNumber(request.contactNumber()),
                request.farmAddress().trim(),
                request.cityCounty(),
                normalizeCrops(request.crops()),
                request.mainActivities().trim(),
                businessNumber,
                request.farmAreaPyeong()
        );

        try {
            return FarmProfileResponse.from(farmProfileRepository.saveAndFlush(profile));
        } catch (DataIntegrityViolationException exception) {
            throw FarmProfileException.dataConflict();
        }
    }

    public FarmProfileResponse getMine(Long userId) {
        getActiveFarmOwner(userId);
        FarmProfile profile = farmProfileRepository.findByOwnerId(userId)
                .orElseThrow(FarmProfileException::profileNotFound);
        return FarmProfileResponse.from(profile);
    }

    @Transactional
    public FarmProfileResponse updateMine(
            Long userId,
            FarmProfileUpdateRequest request
    ) {
        getActiveFarmOwnerForUpdate(userId);
        FarmProfile profile = farmProfileRepository.findByOwnerIdForUpdate(userId)
                .orElseThrow(FarmProfileException::profileNotFound);
        if (!profile.canUpdateBasicInformation()) {
            throw FarmProfileException.profileUpdateNotAllowed();
        }

        String businessNumber = normalizeOptionalNumber(
                request.businessRegistrationNumber()
        );
        if (profile.getStatus() == FarmProfile.FarmProfileStatus.APPROVED
                && profile.ownershipIdentityDiffers(
                request.farmName().trim(),
                request.representativeName().trim(),
                request.farmAddress().trim(),
                request.cityCounty(),
                businessNumber,
                request.farmAreaPyeong()
        ) && jobPostingRepository.existsByFarmProfileOwnerIdAndStatusNotIn(
                userId,
                List.of(
                        JobPosting.JobPostingStatus.CANCELLED,
                        JobPosting.JobPostingStatus.WORK_COMPLETED
                )
        )) {
            throw FarmProfileException.profileUpdateNotAllowed();
        }

        profile.updateBasicInformation(
                request.farmName().trim(),
                request.representativeName().trim(),
                normalizeRequiredNumber(request.contactNumber()),
                request.farmAddress().trim(),
                request.cityCounty(),
                normalizeCrops(request.crops()),
                request.mainActivities().trim(),
                businessNumber,
                request.farmAreaPyeong()
        );
        return FarmProfileResponse.from(profile);
    }

    private User getActiveFarmOwner(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(AuthException::userNotFound);
        if (!user.isActive()) {
            throw AuthException.inactiveAccount();
        }
        if (user.getUserType() != User.UserType.FARM) {
            throw FarmProfileException.farmRoleRequired();
        }
        return user;
    }

    private User getActiveFarmOwnerForUpdate(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(AuthException::userNotFound);
        if (!user.isActive()) {
            throw AuthException.inactiveAccount();
        }
        if (user.getUserType() != User.UserType.FARM) {
            throw FarmProfileException.farmRoleRequired();
        }
        return user;
    }

    private List<String> normalizeCrops(List<String> crops) {
        Map<String, String> uniqueCrops = new LinkedHashMap<>();
        for (String crop : crops) {
            String normalizedCrop = crop.trim();
            uniqueCrops.putIfAbsent(
                    normalizedCrop.toLowerCase(Locale.ROOT),
                    normalizedCrop
            );
        }
        return List.copyOf(uniqueCrops.values());
    }

    private String normalizeRequiredNumber(String value) {
        return value.replaceAll("\\D", "");
    }

    private String normalizeOptionalNumber(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.replaceAll("\\D", "");
    }
}
