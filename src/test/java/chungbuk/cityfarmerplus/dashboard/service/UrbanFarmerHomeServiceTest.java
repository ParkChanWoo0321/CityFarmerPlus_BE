package chungbuk.cityfarmerplus.dashboard.service;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import chungbuk.cityfarmerplus.common.web.PageResponse;
import chungbuk.cityfarmerplus.education.dto.EducationCertificationResponse;
import chungbuk.cityfarmerplus.education.repository.EducationCertificationRepository;
import chungbuk.cityfarmerplus.education.service.EducationProgressService;
import chungbuk.cityfarmerplus.jobposting.dto.PublicJobPostingResponse;
import chungbuk.cityfarmerplus.jobposting.service.PublicJobPostingService;
import chungbuk.cityfarmerplus.urbanfarmer.participation.entity.ParticipationApplication;
import chungbuk.cityfarmerplus.urbanfarmer.participation.repository.ParticipationApplicationRepository;
import chungbuk.cityfarmerplus.urbanfarmer.preference.entity.UrbanFarmerWorkPreference;
import chungbuk.cityfarmerplus.urbanfarmer.preference.repository.UrbanFarmerWorkPreferenceRepository;
import chungbuk.cityfarmerplus.work.repository.WorkAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrbanFarmerHomeServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private EducationCertificationRepository certificationRepository;
    @Mock
    private EducationProgressService educationProgressService;
    @Mock
    private ParticipationApplicationRepository participationRepository;
    @Mock
    private UrbanFarmerWorkPreferenceRepository preferenceRepository;
    @Mock
    private WorkAssignmentRepository assignmentRepository;
    @Mock
    private PublicJobPostingService publicJobPostingService;

    private UrbanFarmerHomeService service;

    @BeforeEach
    void setUp() {
        service = new UrbanFarmerHomeService(
                userRepository,
                certificationRepository,
                educationProgressService,
                participationRepository,
                preferenceRepository,
                assignmentRepository,
                publicJobPostingService
        );
    }

    @Test
    void returnsApplicationAndWorkPreferenceValuesRequiredByHomeDesign() {
        int currentProgramYear = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();
        User user = mock(User.class);
        when(user.isActive()).thenReturn(true);
        when(user.getUserType()).thenReturn(User.UserType.URBAN_FARMER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(certificationRepository.findByUrbanFarmerId(1L)).thenReturn(Optional.empty());
        when(educationProgressService.getProgress(1L, null))
                .thenReturn(EducationCertificationResponse.notSubmitted(1L));

        ParticipationApplication latest = mock(ParticipationApplication.class);
        when(latest.getId()).thenReturn(20L);
        when(latest.getStatus()).thenReturn(ParticipationApplication.ParticipationStatus.SUBMITTED);
        when(latest.getProgramYear()).thenReturn(currentProgramYear);
        when(latest.getSubmittedAt()).thenReturn(Instant.parse("2026-06-25T00:00:00Z"));
        when(participationRepository.findByUrbanFarmerIdAndProgramYear(
                1L,
                currentProgramYear
        )).thenReturn(Optional.of(latest));

        UrbanFarmerWorkPreference preference = mock(UrbanFarmerWorkPreference.class);
        when(preference.getPreferredRegions()).thenReturn(List.of(ChungbukCityCounty.CHUNGJU));
        when(preference.getAvailableDays()).thenReturn(List.of(DayOfWeek.MONDAY));
        when(preferenceRepository.findByUrbanFarmerId(1L)).thenReturn(Optional.of(preference));
        when(assignmentRepository
                .findUpcomingByUrbanFarmerId(
                        eq(1L),
                        any(LocalDate.class),
                        any(LocalTime.class),
                        any(Pageable.class)
                )).thenReturn(List.of());
        when(publicJobPostingService.getOpenPostings(
                eq(1L), eq(null), any(LocalDate.class), eq(null), eq(null), eq(0), eq(5)
        )).thenReturn(new PageResponse<PublicJobPostingResponse>(
                List.of(), 0, 5, 0, 0, false
        ));

        var response = service.get(1L);

        assertThat(response.latestParticipationApplicationId()).isEqualTo(20L);
        assertThat(response.latestParticipationStatus())
                .isEqualTo(ParticipationApplication.ParticipationStatus.SUBMITTED);
        assertThat(response.participationSubmittedAt())
                .isEqualTo(Instant.parse("2026-06-25T00:00:00Z"));
        assertThat(response.workPreferenceRegistered()).isTrue();
        assertThat(response.preferredRegions()).containsExactly(ChungbukCityCounty.CHUNGJU);
        assertThat(response.availableDays()).containsExactly(DayOfWeek.MONDAY);
        verify(participationRepository).findByUrbanFarmerIdAndProgramYear(
                1L,
                currentProgramYear
        );
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(assignmentRepository).findUpcomingByUrbanFarmerId(
                eq(1L),
                any(LocalDate.class),
                any(LocalTime.class),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void returnsEmptyHomeSummaryWhenApplicationAndPreferenceDoNotExist() {
        int currentProgramYear = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();
        User user = mock(User.class);
        when(user.isActive()).thenReturn(true);
        when(user.getUserType()).thenReturn(User.UserType.URBAN_FARMER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(certificationRepository.findByUrbanFarmerId(1L)).thenReturn(Optional.empty());
        when(educationProgressService.getProgress(1L, null))
                .thenReturn(EducationCertificationResponse.notSubmitted(1L));
        when(participationRepository.findByUrbanFarmerIdAndProgramYear(
                1L,
                currentProgramYear
        )).thenReturn(Optional.empty());
        when(preferenceRepository.findByUrbanFarmerId(1L)).thenReturn(Optional.empty());
        when(assignmentRepository
                .findUpcomingByUrbanFarmerId(
                        eq(1L),
                        any(LocalDate.class),
                        any(LocalTime.class),
                        any(Pageable.class)
                )).thenReturn(List.of());
        when(publicJobPostingService.getOpenPostings(
                eq(1L), eq(null), any(LocalDate.class), eq(null), eq(null), eq(0), eq(5)
        )).thenReturn(new PageResponse<PublicJobPostingResponse>(
                List.of(), 0, 5, 0, 0, false
        ));

        var response = service.get(1L);

        assertThat(response.latestParticipationApplicationId()).isNull();
        assertThat(response.participationSubmittedAt()).isNull();
        assertThat(response.workPreferenceRegistered()).isFalse();
        assertThat(response.preferredRegions()).isEmpty();
        assertThat(response.availableDays()).isEmpty();
    }
}
