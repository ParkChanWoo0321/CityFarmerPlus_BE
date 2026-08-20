package chungbuk.cityfarmerplus.urbanfarmer.preference.service;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.common.exception.DomainException;
import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import chungbuk.cityfarmerplus.urbanfarmer.preference.dto.WorkPreferenceRequest;
import chungbuk.cityfarmerplus.urbanfarmer.preference.dto.WorkPreferenceResponse;
import chungbuk.cityfarmerplus.urbanfarmer.preference.entity.UrbanFarmerWorkPreference;
import chungbuk.cityfarmerplus.urbanfarmer.preference.repository.UrbanFarmerWorkPreferenceRepository;
import chungbuk.cityfarmerplus.urbanfarmer.service.UserRoleAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class WorkPreferenceService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final UrbanFarmerWorkPreferenceRepository preferenceRepository;
    private final UserRoleAccessService accessService;

    @Transactional(readOnly = true)
    public WorkPreferenceResponse getMine(Long userId) {
        accessService.requireUrbanFarmer(userId);
        return WorkPreferenceResponse.from(getPreference(userId));
    }

    @Transactional
    public WorkPreferenceResponse upsert(Long userId, WorkPreferenceRequest request) {
        validatePreferredPeriod(
                request.preferredStartDate(),
                request.preferredEndDate()
        );
        User user = accessService.requireUrbanFarmerForUpdate(userId);
        List<ChungbukCityCounty> regions = distinct(request.preferredRegions());
        List<DayOfWeek> days = distinct(request.availableDays());
        List<String> workTypes = normalizeWorkTypes(request.availableWorkTypes());
        String notes = normalizeNullable(request.notes());

        UrbanFarmerWorkPreference preference = preferenceRepository
                .findByUrbanFarmerId(userId)
                .orElseGet(() -> UrbanFarmerWorkPreference.create(
                        user,
                        regions,
                        days,
                        workTypes,
                        request.preferredStartDate(),
                        request.preferredEndDate(),
                        request.canTravel(),
                        notes
                ));
        if (preference.getId() != null) {
            preference.change(
                    regions,
                    days,
                    workTypes,
                    request.preferredStartDate(),
                    request.preferredEndDate(),
                    request.canTravel(),
                    notes
            );
        }
        return WorkPreferenceResponse.from(preferenceRepository.saveAndFlush(preference));
    }

    @Transactional
    public void deleteMine(Long userId) {
        accessService.requireUrbanFarmerForUpdate(userId);
        preferenceRepository.delete(getPreference(userId));
    }

    private UrbanFarmerWorkPreference getPreference(Long userId) {
        return preferenceRepository.findByUrbanFarmerId(userId)
                .orElseThrow(() -> new DomainException(
                        HttpStatus.NOT_FOUND,
                        "WORK_PREFERENCE_NOT_FOUND",
                        "희망 근무 조건을 찾을 수 없습니다."
                ));
    }

    private <T> List<T> distinct(List<T> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private List<String> normalizeWorkTypes(List<String> values) {
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        for (String value : values) {
            String trimmed = value.trim();
            if (trimmed.contains(",") || trimmed.contains("\n") || trimmed.contains("\r")) {
                throw new DomainException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_WORK_TYPE",
                        "작업 유형에는 쉼표나 줄바꿈을 사용할 수 없습니다."
                );
            }
            normalized.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
        }
        return List.copyOf(normalized.values());
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validatePreferredPeriod(
            LocalDate preferredStartDate,
            LocalDate preferredEndDate
    ) {
        if (preferredStartDate == null || preferredEndDate == null) {
            throw new DomainException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_WORK_PREFERENCE_PERIOD",
                    "희망 근무 시작일과 종료일은 필수입니다."
            );
        }
        if (preferredEndDate.isBefore(preferredStartDate)) {
            throw new DomainException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_WORK_PREFERENCE_PERIOD",
                    "희망 근무 종료일은 시작일보다 빠를 수 없습니다."
            );
        }
        if (preferredEndDate.isBefore(LocalDate.now(SERVICE_ZONE))) {
            throw new DomainException(
                    HttpStatus.BAD_REQUEST,
                    "WORK_PREFERENCE_PERIOD_EXPIRED",
                    "이미 종료된 기간은 희망 근무 기간으로 등록할 수 없습니다."
            );
        }
    }
}
