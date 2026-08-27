package chungbuk.cityfarmerplus.admin.proxy.service;

import chungbuk.cityfarmerplus.admin.proxy.dto.ProxyAccountRequest;
import chungbuk.cityfarmerplus.admin.proxy.dto.ProxyEducationSubmissionRequest;
import chungbuk.cityfarmerplus.admin.proxy.dto.ProxyParticipationApplicationRequest;
import chungbuk.cityfarmerplus.admin.proxy.dto.ProxyParticipationSubmitRequest;
import chungbuk.cityfarmerplus.admin.proxy.dto.ProxyUrbanFarmerProfileRequest;
import chungbuk.cityfarmerplus.admin.proxy.dto.ProxyWorkPreferenceRequest;
import chungbuk.cityfarmerplus.auth.dto.SignupRequest;
import chungbuk.cityfarmerplus.auth.dto.UserResponse;
import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.exception.AuthException;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import chungbuk.cityfarmerplus.auth.service.AuthService;
import chungbuk.cityfarmerplus.common.exception.DomainException;
import chungbuk.cityfarmerplus.education.dto.EducationSubmissionRequest;
import chungbuk.cityfarmerplus.education.dto.EducationSubmissionResponse;
import chungbuk.cityfarmerplus.education.service.EducationSubmissionService;
import chungbuk.cityfarmerplus.proxy.entity.ProxyRegistrationLog;
import chungbuk.cityfarmerplus.proxy.repository.ProxyRegistrationLogRepository;
import chungbuk.cityfarmerplus.urbanfarmer.participation.dto.ParticipationApplicationRequest;
import chungbuk.cityfarmerplus.urbanfarmer.participation.dto.ParticipationApplicationResponse;
import chungbuk.cityfarmerplus.urbanfarmer.participation.service.ParticipationApplicationService;
import chungbuk.cityfarmerplus.urbanfarmer.preference.dto.WorkPreferenceRequest;
import chungbuk.cityfarmerplus.urbanfarmer.preference.dto.WorkPreferenceResponse;
import chungbuk.cityfarmerplus.urbanfarmer.preference.service.WorkPreferenceService;
import chungbuk.cityfarmerplus.urbanfarmer.profile.dto.UrbanFarmerProfileRequest;
import chungbuk.cityfarmerplus.urbanfarmer.profile.dto.UrbanFarmerProfileResponse;
import chungbuk.cityfarmerplus.urbanfarmer.profile.service.UrbanFarmerProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminProxyUrbanFarmerService {

    private final AuthService authService;
    private final UrbanFarmerProfileService urbanFarmerProfileService;
    private final WorkPreferenceService workPreferenceService;
    private final ParticipationApplicationService participationApplicationService;
    private final EducationSubmissionService educationSubmissionService;
    private final ProxyRegistrationLogRepository proxyRegistrationLogRepository;
    private final UserRepository userRepository;

    @Transactional
    public UserResponse createAccount(Long adminId, ProxyAccountRequest request) {
        User admin = requireCenterAdmin(adminId);
        UserResponse created = authService.signup(new SignupRequest(
                request.loginId(),
                request.password(),
                request.name(),
                User.UserType.URBAN_FARMER,
                request.phoneNumber(),
                request.birthDate(),
                request.address()
        ));
        User targetUser = userRepository.findById(created.id())
                .orElseThrow(AuthException::userNotFound);
        proxyRegistrationLogRepository.save(ProxyRegistrationLog.record(
                admin,
                targetUser,
                ProxyRegistrationLog.ActionType.URBAN_FARMER_ACCOUNT_CREATED,
                ProxyRegistrationLog.TargetType.USER,
                created.id(),
                request.reason()
        ));
        return created;
    }

    @Transactional
    public UrbanFarmerProfileResponse registerProfile(
            Long adminId,
            Long userId,
            ProxyUrbanFarmerProfileRequest request
    ) {
        User admin = requireCenterAdmin(adminId);
        User targetUser = userRepository.findById(userId)
                .orElseThrow(AuthException::userNotFound);
        UrbanFarmerProfileResponse created = urbanFarmerProfileService.create(
                userId,
                new UrbanFarmerProfileRequest(
                        request.agriculturalBusinessRegistered(),
                        request.experienceCount(),
                        request.notes()
                )
        );
        proxyRegistrationLogRepository.save(ProxyRegistrationLog.record(
                admin,
                targetUser,
                ProxyRegistrationLog.ActionType.URBAN_FARMER_PROFILE_REGISTERED,
                ProxyRegistrationLog.TargetType.URBAN_FARMER_PROFILE,
                created.id(),
                request.reason()
        ));
        return created;
    }

    @Transactional
    public WorkPreferenceResponse registerWorkPreference(
            Long adminId,
            Long userId,
            ProxyWorkPreferenceRequest request
    ) {
        User admin = requireCenterAdmin(adminId);
        User targetUser = userRepository.findById(userId)
                .orElseThrow(AuthException::userNotFound);
        WorkPreferenceResponse result = workPreferenceService.upsert(
                userId,
                new WorkPreferenceRequest(
                        request.preferredRegions(),
                        request.availableDays(),
                        request.availableWorkTypes(),
                        request.preferredStartDate(),
                        request.preferredEndDate(),
                        request.canTravel(),
                        request.notes()
                )
        );
        proxyRegistrationLogRepository.save(ProxyRegistrationLog.record(
                admin,
                targetUser,
                ProxyRegistrationLog.ActionType.WORK_PREFERENCE_REGISTERED,
                ProxyRegistrationLog.TargetType.WORK_PREFERENCE,
                result.id(),
                request.reason()
        ));
        return result;
    }

    @Transactional
    public ParticipationApplicationResponse createParticipationApplication(
            Long adminId,
            Long userId,
            ProxyParticipationApplicationRequest request
    ) {
        User admin = requireCenterAdmin(adminId);
        User targetUser = userRepository.findById(userId)
                .orElseThrow(AuthException::userNotFound);
        ParticipationApplicationResponse created = participationApplicationService.create(
                userId,
                new ParticipationApplicationRequest(
                        request.programYear(),
                        request.agriculturalBusinessRegistered(),
                        request.applicationNote()
                )
        );
        proxyRegistrationLogRepository.save(ProxyRegistrationLog.record(
                admin,
                targetUser,
                ProxyRegistrationLog.ActionType.PARTICIPATION_APPLICATION_CREATED,
                ProxyRegistrationLog.TargetType.PARTICIPATION_APPLICATION,
                created.id(),
                request.reason()
        ));
        return created;
    }

    @Transactional
    public ParticipationApplicationResponse submitParticipationApplication(
            Long adminId,
            Long userId,
            Long applicationId,
            ProxyParticipationSubmitRequest request
    ) {
        User admin = requireCenterAdmin(adminId);
        User targetUser = userRepository.findById(userId)
                .orElseThrow(AuthException::userNotFound);
        ParticipationApplicationResponse submitted = participationApplicationService.submit(
                userId,
                applicationId
        );
        proxyRegistrationLogRepository.save(ProxyRegistrationLog.record(
                admin,
                targetUser,
                ProxyRegistrationLog.ActionType.PARTICIPATION_APPLICATION_SUBMITTED,
                ProxyRegistrationLog.TargetType.PARTICIPATION_APPLICATION,
                submitted.id(),
                request.reason()
        ));
        return submitted;
    }

    @Transactional
    public EducationSubmissionResponse submitEducationCertification(
            Long adminId,
            Long userId,
            ProxyEducationSubmissionRequest request,
            List<MultipartFile> documents
    ) {
        User admin = requireCenterAdmin(adminId);
        User targetUser = userRepository.findById(userId)
                .orElseThrow(AuthException::userNotFound);
        EducationSubmissionResponse created = educationSubmissionService.submit(
                userId,
                new EducationSubmissionRequest(
                        request.courseId(),
                        request.completionDate(),
                        request.completionHours()
                ),
                documents
        );
        proxyRegistrationLogRepository.save(ProxyRegistrationLog.record(
                admin,
                targetUser,
                ProxyRegistrationLog.ActionType.EDUCATION_SUBMISSION_REGISTERED,
                ProxyRegistrationLog.TargetType.EDUCATION_SUBMISSION,
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
