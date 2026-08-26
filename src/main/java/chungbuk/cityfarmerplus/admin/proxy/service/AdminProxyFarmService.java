package chungbuk.cityfarmerplus.admin.proxy.service;

import chungbuk.cityfarmerplus.admin.proxy.dto.ProxyAccountRequest;
import chungbuk.cityfarmerplus.admin.proxy.dto.ProxyFarmOwnershipSubmissionRequest;
import chungbuk.cityfarmerplus.admin.proxy.dto.ProxyFarmProfileRequest;
import chungbuk.cityfarmerplus.admin.proxy.dto.ProxyJobPostingDraftRequest;
import chungbuk.cityfarmerplus.auth.dto.SignupRequest;
import chungbuk.cityfarmerplus.auth.dto.UserResponse;
import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.exception.AuthException;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import chungbuk.cityfarmerplus.auth.service.AuthService;
import chungbuk.cityfarmerplus.common.exception.DomainException;
import chungbuk.cityfarmerplus.farm.dto.FarmProfileCreateRequest;
import chungbuk.cityfarmerplus.farm.dto.FarmProfileResponse;
import chungbuk.cityfarmerplus.farm.ownership.dto.FarmOwnershipSubmissionResponse;
import chungbuk.cityfarmerplus.farm.ownership.service.FarmOwnershipSubmissionService;
import chungbuk.cityfarmerplus.farm.service.FarmProfileService;
import chungbuk.cityfarmerplus.jobposting.dto.JobPostingResponse;
import chungbuk.cityfarmerplus.jobposting.service.FarmJobPostingService;
import chungbuk.cityfarmerplus.proxy.entity.ProxyRegistrationLog;
import chungbuk.cityfarmerplus.proxy.repository.ProxyRegistrationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminProxyFarmService {

    private final AuthService authService;
    private final FarmProfileService farmProfileService;
    private final FarmOwnershipSubmissionService farmOwnershipSubmissionService;
    private final FarmJobPostingService farmJobPostingService;
    private final ProxyRegistrationLogRepository proxyRegistrationLogRepository;
    private final UserRepository userRepository;

    @Transactional
    public UserResponse createAccount(Long adminId, ProxyAccountRequest request) {
        User admin = requireCenterAdmin(adminId);
        UserResponse created = authService.signup(new SignupRequest(
                request.loginId(),
                request.password(),
                request.name(),
                User.UserType.FARM,
                request.phoneNumber(),
                request.birthDate(),
                request.address()
        ));
        User targetUser = userRepository.findById(created.id())
                .orElseThrow(AuthException::userNotFound);
        proxyRegistrationLogRepository.save(ProxyRegistrationLog.record(
                admin,
                targetUser,
                ProxyRegistrationLog.ActionType.FARM_ACCOUNT_CREATED,
                ProxyRegistrationLog.TargetType.USER,
                created.id(),
                request.reason()
        ));
        return created;
    }

    @Transactional
    public FarmProfileResponse registerProfile(
            Long adminId,
            Long userId,
            ProxyFarmProfileRequest request
    ) {
        User admin = requireCenterAdmin(adminId);
        User targetUser = userRepository.findById(userId)
                .orElseThrow(AuthException::userNotFound);
        FarmProfileResponse created = farmProfileService.create(
                userId,
                new FarmProfileCreateRequest(
                        request.farmName(),
                        request.representativeName(),
                        request.contactNumber(),
                        request.farmAddress(),
                        request.cityCounty(),
                        request.crops(),
                        request.mainActivities(),
                        request.businessRegistrationNumber(),
                        request.farmAreaPyeong()
                )
        );
        proxyRegistrationLogRepository.save(ProxyRegistrationLog.record(
                admin,
                targetUser,
                ProxyRegistrationLog.ActionType.FARM_PROFILE_REGISTERED,
                ProxyRegistrationLog.TargetType.FARM_PROFILE,
                created.id(),
                request.reason()
        ));
        return created;
    }

    @Transactional
    public FarmOwnershipSubmissionResponse submitOwnershipDocuments(
            Long adminId,
            Long userId,
            ProxyFarmOwnershipSubmissionRequest request,
            List<MultipartFile> documents
    ) {
        User admin = requireCenterAdmin(adminId);
        User targetUser = userRepository.findById(userId)
                .orElseThrow(AuthException::userNotFound);
        FarmOwnershipSubmissionResponse created = farmOwnershipSubmissionService.submit(
                userId,
                documents
        );
        proxyRegistrationLogRepository.save(ProxyRegistrationLog.record(
                admin,
                targetUser,
                ProxyRegistrationLog.ActionType.FARM_OWNERSHIP_SUBMISSION_REGISTERED,
                ProxyRegistrationLog.TargetType.FARM_OWNERSHIP_SUBMISSION,
                created.id(),
                request.reason()
        ));
        return created;
    }

    @Transactional
    public JobPostingResponse createJobPostingDraft(
            Long adminId,
            Long userId,
            ProxyJobPostingDraftRequest request,
            boolean submitForReview
    ) {
        User admin = requireCenterAdmin(adminId);
        User targetUser = userRepository.findById(userId)
                .orElseThrow(AuthException::userNotFound);
        JobPostingResponse created = farmJobPostingService.create(
                userId,
                request.toUpsertRequest(),
                submitForReview
        );
        proxyRegistrationLogRepository.save(ProxyRegistrationLog.record(
                admin,
                targetUser,
                ProxyRegistrationLog.ActionType.JOB_POSTING_DRAFT_CREATED,
                ProxyRegistrationLog.TargetType.JOB_POSTING,
                created.id(),
                request.reason()
        ));
        return created;
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
