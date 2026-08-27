package chungbuk.cityfarmerplus.admin.farm.service;

import chungbuk.cityfarmerplus.admin.farm.dto.FarmOwnershipRejectRequest;
import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.exception.AuthException;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import chungbuk.cityfarmerplus.common.exception.DomainException;
import chungbuk.cityfarmerplus.farm.dto.FarmProfileResponse;
import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import chungbuk.cityfarmerplus.farm.exception.FarmProfileException;
import chungbuk.cityfarmerplus.farm.ownership.dto.FarmOwnershipSubmissionResponse;
import chungbuk.cityfarmerplus.farm.ownership.entity.FarmOwnershipSubmission;
import chungbuk.cityfarmerplus.farm.ownership.exception.FarmOwnershipException;
import chungbuk.cityfarmerplus.farm.ownership.repository.FarmOwnershipSubmissionRepository;
import chungbuk.cityfarmerplus.farm.repository.FarmProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminFarmOwnershipService {

    private final FarmProfileRepository farmProfileRepository;
    private final FarmOwnershipSubmissionRepository submissionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<FarmProfileResponse> list(FarmProfile.FarmProfileStatus status) {
        List<FarmProfile> profiles = status == null
                ? farmProfileRepository.findAllByOrderByUpdatedAtDesc()
                : farmProfileRepository.findAllByStatusOrderByUpdatedAtDesc(status);

        return profiles
                .stream()
                .map(FarmProfileResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public FarmOwnershipSubmissionResponse getDetail(Long profileId) {
        FarmProfile profile = farmProfileRepository.findById(profileId)
                .orElseThrow(FarmProfileException::profileNotFound);
        return submissionRepository.findAllDetailedByFarmProfileId(profileId)
                .stream()
                .findFirst()
                .map(submission -> FarmOwnershipSubmissionResponse.from(submission, profile.getStatus()))
                .orElseThrow(FarmOwnershipException::submissionNotFound);
    }

    @Transactional
    public FarmOwnershipSubmissionResponse approve(Long adminId, Long profileId) {
        User reviewer = requireCenterAdmin(adminId);
        FarmProfile profile = getProfileForUpdate(profileId);
        FarmOwnershipSubmission submission = getLatestSubmissionForUpdate(profileId);
        Instant now = Instant.now();
        try {
            submission.approve(reviewer, now);
            profile.approveOwnership(reviewer, now);
        } catch (IllegalStateException exception) {
            throw FarmOwnershipException.reviewNotAllowed();
        }
        return FarmOwnershipSubmissionResponse.from(submission, profile.getStatus());
    }

    @Transactional
    public FarmOwnershipSubmissionResponse reject(
            Long adminId,
            Long profileId,
            FarmOwnershipRejectRequest request
    ) {
        User reviewer = requireCenterAdmin(adminId);
        FarmProfile profile = getProfileForUpdate(profileId);
        FarmOwnershipSubmission submission = getLatestSubmissionForUpdate(profileId);
        Instant now = Instant.now();
        try {
            submission.reject(reviewer, now, request.reason());
            profile.rejectOwnership(reviewer, now, request.reason());
        } catch (IllegalStateException exception) {
            throw FarmOwnershipException.reviewNotAllowed();
        }
        return FarmOwnershipSubmissionResponse.from(submission, profile.getStatus());
    }

    private FarmProfile getProfileForUpdate(Long profileId) {
        return farmProfileRepository.findByIdForUpdate(profileId)
                .orElseThrow(FarmProfileException::profileNotFound);
    }

    private FarmOwnershipSubmission getLatestSubmissionForUpdate(Long profileId) {
        return submissionRepository.findLatestByFarmProfileIdForUpdate(profileId)
                .orElseThrow(FarmOwnershipException::reviewNotAllowed);
    }

    private User requireCenterAdmin(Long adminId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(AuthException::userNotFound);
        if (!admin.isActive()) {
            throw AuthException.inactiveAccount();
        }
        if (admin.getUserType() != User.UserType.CENTER_ADMIN) {
            throw new DomainException(
                    HttpStatus.FORBIDDEN,
                    "CENTER_ADMIN_ROLE_REQUIRED",
                    "관리자 계정만 사용할 수 있습니다."
            );
        }
        return admin;
    }
}
