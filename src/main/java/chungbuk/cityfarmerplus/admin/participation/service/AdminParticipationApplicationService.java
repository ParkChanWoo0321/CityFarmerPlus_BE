package chungbuk.cityfarmerplus.admin.participation.service;

import chungbuk.cityfarmerplus.admin.participation.dto.ParticipationRejectRequest;
import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.exception.AuthException;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import chungbuk.cityfarmerplus.common.exception.DomainException;
import chungbuk.cityfarmerplus.urbanfarmer.participation.dto.ParticipationApplicationResponse;
import chungbuk.cityfarmerplus.urbanfarmer.participation.entity.ParticipationApplication;
import chungbuk.cityfarmerplus.urbanfarmer.participation.repository.ParticipationApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminParticipationApplicationService {

    private final ParticipationApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final Clock clock = Clock.systemUTC();

    @Transactional(readOnly = true)
    public List<ParticipationApplicationResponse> list() {
        return applicationRepository.findAll().stream()
                .sorted(Comparator.comparing(ParticipationApplication::getCreatedAt).reversed())
                .map(ParticipationApplicationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ParticipationApplicationResponse getDetail(Long applicationId) {
        return ParticipationApplicationResponse.from(getApplication(applicationId));
    }

    @Transactional
    public ParticipationApplicationResponse approve(Long adminId, Long applicationId) {
        User admin = requireCenterAdmin(adminId);
        ParticipationApplication application = getApplication(applicationId);
        try {
            application.approve(admin, Instant.now(clock));
        } catch (IllegalStateException exception) {
            throw invalidState(exception.getMessage());
        }
        return ParticipationApplicationResponse.from(application);
    }

    @Transactional
    public ParticipationApplicationResponse reject(
            Long adminId,
            Long applicationId,
            ParticipationRejectRequest request
    ) {
        User admin = requireCenterAdmin(adminId);
        ParticipationApplication application = getApplication(applicationId);
        try {
            application.reject(admin, request.reason(), Instant.now(clock));
        } catch (IllegalStateException exception) {
            throw invalidState(exception.getMessage());
        }
        return ParticipationApplicationResponse.from(application);
    }

    private ParticipationApplication getApplication(Long applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(this::notFound);
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

    private DomainException notFound() {
        return new DomainException(
                HttpStatus.NOT_FOUND,
                "PARTICIPATION_APPLICATION_NOT_FOUND",
                "사업참여 신청을 찾을 수 없습니다."
        );
    }

    private DomainException invalidState(String message) {
        return new DomainException(HttpStatus.CONFLICT, "INVALID_PARTICIPATION_STATUS", message);
    }
}
