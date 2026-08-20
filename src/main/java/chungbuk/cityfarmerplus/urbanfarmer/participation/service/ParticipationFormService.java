package chungbuk.cityfarmerplus.urbanfarmer.participation.service;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.common.exception.DomainException;
import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import chungbuk.cityfarmerplus.urbanfarmer.participation.dto.ParticipationFormRequest;
import chungbuk.cityfarmerplus.urbanfarmer.participation.dto.ParticipationFormResponse;
import chungbuk.cityfarmerplus.urbanfarmer.participation.entity.ParticipationApplication;
import chungbuk.cityfarmerplus.urbanfarmer.participation.repository.ParticipationApplicationRepository;
import chungbuk.cityfarmerplus.urbanfarmer.preference.entity.UrbanFarmerWorkPreference;
import chungbuk.cityfarmerplus.urbanfarmer.preference.repository.UrbanFarmerWorkPreferenceRepository;
import chungbuk.cityfarmerplus.urbanfarmer.profile.entity.UrbanFarmerProfile;
import chungbuk.cityfarmerplus.urbanfarmer.profile.repository.UrbanFarmerProfileRepository;
import chungbuk.cityfarmerplus.urbanfarmer.service.UserRoleAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Service
public class ParticipationFormService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final ParticipationApplicationRepository applicationRepository;
    private final UrbanFarmerProfileRepository profileRepository;
    private final UrbanFarmerWorkPreferenceRepository preferenceRepository;
    private final UserRoleAccessService accessService;
    private final Clock clock;

    @Autowired
    public ParticipationFormService(
            ParticipationApplicationRepository applicationRepository,
            UrbanFarmerProfileRepository profileRepository,
            UrbanFarmerWorkPreferenceRepository preferenceRepository,
            UserRoleAccessService accessService
    ) {
        this(
                applicationRepository,
                profileRepository,
                preferenceRepository,
                accessService,
                Clock.systemUTC()
        );
    }

    ParticipationFormService(
            ParticipationApplicationRepository applicationRepository,
            UrbanFarmerProfileRepository profileRepository,
            UrbanFarmerWorkPreferenceRepository preferenceRepository,
            UserRoleAccessService accessService,
            Clock clock
    ) {
        this.applicationRepository = applicationRepository;
        this.profileRepository = profileRepository;
        this.preferenceRepository = preferenceRepository;
        this.accessService = accessService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ParticipationFormResponse getMine(Long userId, int programYear) {
        validateProgramYear(programYear);
        accessService.requireUrbanFarmer(userId);
        ParticipationApplication application = applicationRepository
                .findByUrbanFarmerIdAndProgramYear(userId, programYear)
                .orElse(null);
        UrbanFarmerProfile profile = profileRepository
                .findByUrbanFarmerId(userId)
                .orElse(null);
        UrbanFarmerWorkPreference preference = preferenceRepository
                .findByUrbanFarmerId(userId)
                .orElse(null);
        return ParticipationFormResponse.from(
                programYear,
                application,
                profile,
                preference
        );
    }

    @Transactional
    public ParticipationFormResponse save(
            Long userId,
            int programYear,
            ParticipationFormRequest request
    ) {
        return write(userId, programYear, request, false);
    }

    @Transactional
    public ParticipationFormResponse submit(
            Long userId,
            int programYear,
            ParticipationFormRequest request
    ) {
        return write(userId, programYear, request, true);
    }

    private ParticipationFormResponse write(
            Long userId,
            int programYear,
            ParticipationFormRequest request,
            boolean submit
    ) {
        validateProgramYear(programYear);
        validateRequest(request);

        // Every writer acquires locks in the same order to prevent cross-feature deadlocks.
        User user = accessService.requireUrbanFarmerForUpdate(userId);
        Optional<ParticipationApplication> applicationOptional = applicationRepository
                .findByUrbanFarmerIdAndProgramYearForUpdate(userId, programYear);
        Optional<UrbanFarmerProfile> profileOptional = profileRepository
                .findByUrbanFarmerIdForUpdate(userId);
        Optional<UrbanFarmerWorkPreference> preferenceOptional = preferenceRepository
                .findByUrbanFarmerIdForUpdate(userId);

        validateExpectedVersion(
                "application",
                request.expectedApplicationVersion(),
                applicationOptional.map(ParticipationApplication::getVersion).orElse(null)
        );
        validateExpectedVersion(
                "profile",
                request.expectedProfileVersion(),
                profileOptional.map(UrbanFarmerProfile::getVersion).orElse(null)
        );
        validateExpectedVersion(
                "workPreference",
                request.expectedWorkPreferenceVersion(),
                preferenceOptional.map(UrbanFarmerWorkPreference::getVersion).orElse(null)
        );

        ParticipationApplication application = applicationOptional.orElse(null);
        if (application != null
                && application.getStatus()
                == ParticipationApplication.ParticipationStatus.CANCELLED) {
            throw invalidStatus("취소한 사업연도 신청서는 수정하거나 재제출할 수 없습니다.");
        }

        String experienceNotes = normalizeNullable(request.experienceNotes());
        String preferenceNotes = normalizeNullable(request.workPreferenceNotes());
        String applicationNote = normalizeNullable(request.applicationNote());
        List<ChungbukCityCounty> regions = distinct(request.preferredRegions());
        List<DayOfWeek> days = distinct(request.availableDays());
        List<String> workTypes = normalizeWorkTypes(request.availableWorkTypes());

        if (application != null
                && application.getStatus()
                == ParticipationApplication.ParticipationStatus.APPROVED) {
            if (submit) {
                throw invalidStatus("승인된 신청서는 다시 제출할 수 없습니다.");
            }
            validateApprovedFieldsLocked(application, request, applicationNote);
        }

        UrbanFarmerProfile profile = upsertProfile(
                user,
                profileOptional.orElse(null),
                application,
                request,
                experienceNotes
        );
        UrbanFarmerWorkPreference preference = upsertPreference(
                user,
                preferenceOptional.orElse(null),
                request,
                regions,
                days,
                workTypes,
                preferenceNotes
        );
        application = upsertApplication(
                user,
                programYear,
                application,
                request,
                applicationNote,
                submit
        );

        profileRepository.save(profile);
        preferenceRepository.save(preference);
        applicationRepository.save(application);
        applicationRepository.flush();

        return ParticipationFormResponse.from(
                programYear,
                application,
                profile,
                preference
        );
    }

    private UrbanFarmerProfile upsertProfile(
            User user,
            UrbanFarmerProfile profile,
            ParticipationApplication application,
            ParticipationFormRequest request,
            String experienceNotes
    ) {
        boolean approved = application != null
                && application.getStatus()
                == ParticipationApplication.ParticipationStatus.APPROVED;
        boolean agriculturalBusinessRegistered = approved
                ? application.isAgriculturalBusinessRegistered()
                : request.agriculturalBusinessRegistered();
        if (profile == null) {
            return UrbanFarmerProfile.create(
                    user,
                    agriculturalBusinessRegistered,
                    request.experienceCount(),
                    experienceNotes
            );
        }
        if (approved) {
            profile.updateExperience(request.experienceCount(), experienceNotes);
        } else {
            profile.update(
                    agriculturalBusinessRegistered,
                    request.experienceCount(),
                    experienceNotes
            );
        }
        return profile;
    }

    private UrbanFarmerWorkPreference upsertPreference(
            User user,
            UrbanFarmerWorkPreference preference,
            ParticipationFormRequest request,
            List<ChungbukCityCounty> regions,
            List<DayOfWeek> days,
            List<String> workTypes,
            String preferenceNotes
    ) {
        if (preference == null) {
            return UrbanFarmerWorkPreference.create(
                    user,
                    regions,
                    days,
                    workTypes,
                    request.preferredStartDate(),
                    request.preferredEndDate(),
                    request.canTravel(),
                    preferenceNotes
            );
        }
        preference.change(
                regions,
                days,
                workTypes,
                request.preferredStartDate(),
                request.preferredEndDate(),
                request.canTravel(),
                preferenceNotes
        );
        return preference;
    }

    private ParticipationApplication upsertApplication(
            User user,
            int programYear,
            ParticipationApplication application,
            ParticipationFormRequest request,
            String applicationNote,
            boolean submit
    ) {
        if (application == null) {
            application = ParticipationApplication.createDraft(
                    user,
                    programYear,
                    request.agriculturalBusinessRegistered(),
                    applicationNote
            );
        } else {
            switch (application.getStatus()) {
                case DRAFT -> application.updateDraft(
                        request.agriculturalBusinessRegistered(),
                        applicationNote
                );
                case SUBMITTED -> application.updateSubmitted(
                        request.agriculturalBusinessRegistered(),
                        applicationNote
                );
                case REJECTED -> application.updateRejected(
                        request.agriculturalBusinessRegistered(),
                        applicationNote
                );
                case APPROVED -> {
                    return application;
                }
                case CANCELLED -> throw invalidStatus(
                        "취소한 사업연도 신청서는 수정하거나 재제출할 수 없습니다."
                );
            }
        }

        if (!submit) {
            return application;
        }
        Instant now = Instant.now(clock);
        switch (application.getStatus()) {
            case DRAFT -> application.submit(now);
            case REJECTED -> application.resubmit(now);
            case SUBMITTED -> throw invalidStatus("이미 제출된 신청서입니다.");
            case APPROVED -> throw invalidStatus("승인된 신청서는 다시 제출할 수 없습니다.");
            case CANCELLED -> throw invalidStatus("취소한 신청서는 다시 제출할 수 없습니다.");
        }
        return application;
    }

    private void validateApprovedFieldsLocked(
            ParticipationApplication application,
            ParticipationFormRequest request,
            String applicationNote
    ) {
        if (application.isAgriculturalBusinessRegistered()
                != request.agriculturalBusinessRegistered()
                || !Objects.equals(application.getApplicationNote(), applicationNote)) {
            throw new DomainException(
                    HttpStatus.CONFLICT,
                    "APPROVED_PARTICIPATION_FIELDS_LOCKED",
                    "승인된 신청서의 농업경영체 등록 여부와 신청 특이사항은 변경할 수 없습니다."
            );
        }
    }

    private void validateExpectedVersion(
            String resource,
            Long expectedVersion,
            Long currentVersion
    ) {
        if (expectedVersion == null) {
            return;
        }
        if (!Objects.equals(expectedVersion, currentVersion)) {
            throw new DomainException(
                    HttpStatus.CONFLICT,
                    "PARTICIPATION_FORM_VERSION_CONFLICT",
                    resource + " 정보가 다른 요청에 의해 변경되었습니다. 최신 정보를 다시 조회해 주세요."
            );
        }
    }

    private void validateProgramYear(int programYear) {
        if (programYear < 2000 || programYear > 2100) {
            throw new DomainException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PROGRAM_YEAR",
                    "사업연도는 2000년 이상 2100년 이하여야 합니다."
            );
        }
    }

    private void validateRequest(ParticipationFormRequest request) {
        if (request == null
                || request.agriculturalBusinessRegistered() == null
                || request.experienceCount() == null
                || request.experienceCount() < 0
                || request.preferredRegions() == null
                || request.preferredRegions().isEmpty()
                || request.availableDays() == null
                || request.availableDays().isEmpty()
                || request.availableWorkTypes() == null
                || request.availableWorkTypes().isEmpty()
                || request.canTravel() == null) {
            throw new DomainException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PARTICIPATION_FORM",
                    "신청서 필수 입력값을 확인해 주세요."
            );
        }
        validatePreferredPeriod(
                request.preferredStartDate(),
                request.preferredEndDate()
        );
    }

    private void validatePreferredPeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new DomainException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PARTICIPATION_FORM_PERIOD",
                    "희망 근무 시작일과 종료일을 올바르게 입력해 주세요."
            );
        }
        if (endDate.isBefore(LocalDate.now(clock.withZone(SERVICE_ZONE)))) {
            throw new DomainException(
                    HttpStatus.BAD_REQUEST,
                    "PARTICIPATION_FORM_PERIOD_EXPIRED",
                    "이미 종료된 기간은 희망 근무 기간으로 등록할 수 없습니다."
            );
        }
    }

    private <T> List<T> distinct(List<T> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private List<String> normalizeWorkTypes(List<String> values) {
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new DomainException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_PARTICIPATION_FORM",
                        "가능한 작업 유형은 빈 값일 수 없습니다."
                );
            }
            String trimmed = value.trim();
            if (trimmed.contains(",") || trimmed.contains("\n") || trimmed.contains("\r")) {
                throw new DomainException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_WORK_TYPE",
                        "가능한 작업 유형에는 쉼표나 줄바꿈을 사용할 수 없습니다."
                );
            }
            normalized.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
        }
        return List.copyOf(normalized.values());
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private DomainException invalidStatus(String message) {
        return new DomainException(
                HttpStatus.CONFLICT,
                "INVALID_PARTICIPATION_FORM_STATUS",
                message
        );
    }
}
