package chungbuk.cityfarmerplus.urbanfarmer.participation.service;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.common.exception.DomainException;
import chungbuk.cityfarmerplus.urbanfarmer.participation.dto.ParticipationApplicationRequest;
import chungbuk.cityfarmerplus.urbanfarmer.participation.dto.ParticipationApplicationResponse;
import chungbuk.cityfarmerplus.urbanfarmer.participation.dto.ParticipationApplicationUpdateRequest;
import chungbuk.cityfarmerplus.urbanfarmer.participation.entity.ParticipationApplication;
import chungbuk.cityfarmerplus.urbanfarmer.participation.repository.ParticipationApplicationRepository;
import chungbuk.cityfarmerplus.urbanfarmer.service.UserRoleAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ParticipationApplicationService {

    private final ParticipationApplicationRepository applicationRepository;
    private final UserRoleAccessService accessService;
    private final Clock clock = Clock.systemUTC();

    @Transactional
    public ParticipationApplicationResponse create(
            Long userId,
            ParticipationApplicationRequest request
    ) {
        User user = accessService.requireUrbanFarmerForUpdate(userId);
        if (request.programYear() == null) {
            throw error(HttpStatus.BAD_REQUEST, "PROGRAM_YEAR_REQUIRED", "사업연도를 입력해야 합니다.");
        }
        if (applicationRepository.existsByUrbanFarmerIdAndProgramYear(
                userId,
                request.programYear()
        )) {
            throw error(
                    HttpStatus.CONFLICT,
                    "PARTICIPATION_APPLICATION_ALREADY_EXISTS",
                    "해당 사업연도의 참여 신청이 이미 존재합니다."
            );
        }
        ParticipationApplication application = ParticipationApplication.createDraft(
                user,
                request.programYear(),
                request.agriculturalBusinessRegistered(),
                normalizeNullable(request.applicationNote())
        );
        return ParticipationApplicationResponse.from(
                applicationRepository.saveAndFlush(application)
        );
    }

    @Transactional(readOnly = true)
    public List<ParticipationApplicationResponse> getMine(Long userId) {
        accessService.requireUrbanFarmer(userId);
        return applicationRepository
                .findAllByUrbanFarmerIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(ParticipationApplicationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ParticipationApplicationResponse getMine(Long userId, Long applicationId) {
        accessService.requireUrbanFarmer(userId);
        return ParticipationApplicationResponse.from(getOwned(applicationId, userId));
    }

    @Transactional
    public ParticipationApplicationResponse update(
            Long userId,
            Long applicationId,
            ParticipationApplicationUpdateRequest request
    ) {
        accessService.requireUrbanFarmerForUpdate(userId);
        ParticipationApplication application = getOwned(applicationId, userId);
        try {
            application.updateDraft(
                    request.agriculturalBusinessRegistered(),
                    normalizeNullable(request.applicationNote())
            );
        } catch (IllegalStateException exception) {
            throw invalidState(exception.getMessage());
        }
        return ParticipationApplicationResponse.from(application);
    }

    @Transactional
    public void deleteDraft(Long userId, Long applicationId) {
        accessService.requireUrbanFarmerForUpdate(userId);
        ParticipationApplication application = getOwned(applicationId, userId);
        if (!application.canDeleteDraft()) {
            throw invalidState("초안 상태의 신청서만 삭제할 수 있습니다.");
        }
        applicationRepository.delete(application);
    }

    @Transactional
    public ParticipationApplicationResponse submit(Long userId, Long applicationId) {
        accessService.requireUrbanFarmerForUpdate(userId);
        ParticipationApplication application = getOwned(applicationId, userId);
        try {
            application.submit(Instant.now(clock));
        } catch (IllegalStateException exception) {
            throw invalidState(exception.getMessage());
        }
        return ParticipationApplicationResponse.from(application);
    }

    @Transactional
    public ParticipationApplicationResponse cancel(Long userId, Long applicationId) {
        accessService.requireUrbanFarmerForUpdate(userId);
        ParticipationApplication application = getOwned(applicationId, userId);
        try {
            application.cancel(Instant.now(clock));
        } catch (IllegalStateException exception) {
            throw invalidState(exception.getMessage());
        }
        return ParticipationApplicationResponse.from(application);
    }

    private ParticipationApplication getOwned(Long applicationId, Long userId) {
        return applicationRepository.findByIdAndUrbanFarmerId(applicationId, userId)
                .orElseThrow(this::notFound);
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private DomainException notFound() {
        return error(
                HttpStatus.NOT_FOUND,
                "PARTICIPATION_APPLICATION_NOT_FOUND",
                "사업참여 신청을 찾을 수 없습니다."
        );
    }

    private DomainException invalidState(String message) {
        return error(HttpStatus.CONFLICT, "INVALID_PARTICIPATION_STATUS", message);
    }

    private DomainException error(HttpStatus status, String code, String message) {
        return new DomainException(status, code, message);
    }
}
