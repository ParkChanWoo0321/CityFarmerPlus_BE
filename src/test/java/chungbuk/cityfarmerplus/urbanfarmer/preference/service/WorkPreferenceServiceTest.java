package chungbuk.cityfarmerplus.urbanfarmer.preference.service;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.common.exception.DomainException;
import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import chungbuk.cityfarmerplus.urbanfarmer.preference.dto.WorkPreferenceRequest;
import chungbuk.cityfarmerplus.urbanfarmer.preference.entity.UrbanFarmerWorkPreference;
import chungbuk.cityfarmerplus.urbanfarmer.preference.repository.UrbanFarmerWorkPreferenceRepository;
import chungbuk.cityfarmerplus.urbanfarmer.service.UserRoleAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkPreferenceServiceTest {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    @Mock
    private UrbanFarmerWorkPreferenceRepository preferenceRepository;
    @Mock
    private UserRoleAccessService accessService;

    private WorkPreferenceService service;

    @BeforeEach
    void setUp() {
        service = new WorkPreferenceService(preferenceRepository, accessService);
    }

    @Test
    void createsPreferenceWithPreferredDatePeriod() {
        User user = urbanFarmer();
        LocalDate startDate = LocalDate.now(SERVICE_ZONE).plusDays(1);
        LocalDate endDate = startDate.plusDays(30);
        when(accessService.requireUrbanFarmerForUpdate(1L)).thenReturn(user);
        when(preferenceRepository.findByUrbanFarmerId(1L)).thenReturn(Optional.empty());
        when(preferenceRepository.saveAndFlush(any(UrbanFarmerWorkPreference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.upsert(1L, request(startDate, endDate));

        assertThat(response.preferredStartDate()).isEqualTo(startDate);
        assertThat(response.preferredEndDate()).isEqualTo(endDate);
        verify(preferenceRepository).saveAndFlush(any(UrbanFarmerWorkPreference.class));
    }

    @Test
    void rejectsReversedDatePeriodBeforePersistence() {
        LocalDate startDate = LocalDate.now(SERVICE_ZONE).plusDays(2);

        assertThatThrownBy(() -> service.upsert(
                1L,
                request(startDate, startDate.minusDays(1))
        ))
                .isInstanceOfSatisfying(DomainException.class, exception ->
                        assertThat(exception.getCode())
                                .isEqualTo("INVALID_WORK_PREFERENCE_PERIOD"));

        verify(preferenceRepository, never()).saveAndFlush(any());
    }

    @Test
    void updatesPreferredDatePeriodOnExistingPreference() {
        User user = urbanFarmer();
        LocalDate today = LocalDate.now(SERVICE_ZONE);
        UrbanFarmerWorkPreference preference = UrbanFarmerWorkPreference.create(
                user,
                List.of(ChungbukCityCounty.CHUNGJU),
                List.of(DayOfWeek.MONDAY),
                List.of("수확 보조"),
                today,
                today.plusDays(7),
                true,
                null
        );
        ReflectionTestUtils.setField(preference, "id", 10L);
        when(accessService.requireUrbanFarmerForUpdate(1L)).thenReturn(user);
        when(preferenceRepository.findByUrbanFarmerId(1L))
                .thenReturn(Optional.of(preference));
        when(preferenceRepository.saveAndFlush(preference)).thenReturn(preference);
        LocalDate newStartDate = today.plusDays(10);
        LocalDate newEndDate = today.plusDays(20);

        var response = service.upsert(1L, request(newStartDate, newEndDate));

        assertThat(response.preferredStartDate()).isEqualTo(newStartDate);
        assertThat(response.preferredEndDate()).isEqualTo(newEndDate);
        assertThat(preference.getPreferredStartDate()).isEqualTo(newStartDate);
        assertThat(preference.getPreferredEndDate()).isEqualTo(newEndDate);
    }

    @Test
    void rejectsAlreadyExpiredDatePeriod() {
        LocalDate yesterday = LocalDate.now(SERVICE_ZONE).minusDays(1);

        assertThatThrownBy(() -> service.upsert(
                1L,
                request(yesterday.minusDays(10), yesterday)
        ))
                .isInstanceOfSatisfying(DomainException.class, exception ->
                        assertThat(exception.getCode())
                                .isEqualTo("WORK_PREFERENCE_PERIOD_EXPIRED"));

        verify(preferenceRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsCommaDelimitedWorkTypeBeforePersistence() {
        User user = urbanFarmer();
        LocalDate startDate = LocalDate.now(SERVICE_ZONE).plusDays(1);
        when(accessService.requireUrbanFarmerForUpdate(1L)).thenReturn(user);

        WorkPreferenceRequest request = new WorkPreferenceRequest(
                List.of(ChungbukCityCounty.CHUNGJU),
                List.of(DayOfWeek.MONDAY),
                List.of("수확, 선별"),
                startDate,
                startDate.plusDays(7),
                true,
                null
        );

        assertThatThrownBy(() -> service.upsert(1L, request))
                .isInstanceOfSatisfying(DomainException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("INVALID_WORK_TYPE"));

        verify(preferenceRepository, never()).saveAndFlush(any());
    }

    private WorkPreferenceRequest request(LocalDate startDate, LocalDate endDate) {
        return new WorkPreferenceRequest(
                List.of(ChungbukCityCounty.CHUNGJU),
                List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                List.of("수확 보조"),
                startDate,
                endDate,
                true,
                "대중교통으로 이동 가능합니다."
        );
    }

    private User urbanFarmer() {
        return User.register(
                "urban_preference_service",
                "encoded-password",
                "도시농부",
                User.UserType.URBAN_FARMER
        );
    }
}
