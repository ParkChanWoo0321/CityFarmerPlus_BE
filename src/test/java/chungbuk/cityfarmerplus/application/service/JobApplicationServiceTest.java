package chungbuk.cityfarmerplus.application.service;

import chungbuk.cityfarmerplus.application.entity.JobApplication;
import chungbuk.cityfarmerplus.application.repository.JobApplicationRepository;
import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import chungbuk.cityfarmerplus.education.service.EducationEligibilityService;
import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;
import chungbuk.cityfarmerplus.jobposting.repository.JobPostingRepository;
import chungbuk.cityfarmerplus.urbanfarmer.preference.entity.UrbanFarmerWorkPreference;
import chungbuk.cityfarmerplus.urbanfarmer.preference.repository.UrbanFarmerWorkPreferenceRepository;
import chungbuk.cityfarmerplus.urbanfarmer.profile.entity.UrbanFarmerProfile;
import chungbuk.cityfarmerplus.urbanfarmer.profile.repository.UrbanFarmerProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobApplicationServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long POSTING_ID = 10L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private JobPostingRepository postingRepository;
    @Mock
    private JobApplicationRepository applicationRepository;
    @Mock
    private EducationEligibilityService eligibilityService;
    @Mock
    private UrbanFarmerWorkPreferenceRepository preferenceRepository;
    @Mock
    private UrbanFarmerProfileRepository profileRepository;

    private JobApplicationService service;

    @BeforeEach
    void setUp() {
        service = new JobApplicationService(
                userRepository,
                postingRepository,
                applicationRepository,
                eligibilityService,
                preferenceRepository,
                profileRepository
        );
    }

    @Test
    void applyCapturesEveryCurrentSupportCondition() {
        User urbanFarmer = urbanFarmer();
        JobPosting posting = openPosting();
        UrbanFarmerWorkPreference preference = preference(
                urbanFarmer,
                List.of(ChungbukCityCounty.CHEONGJU, ChungbukCityCounty.CHUNGJU),
                List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                List.of("HARVEST", "SORTING"),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 10, 31),
                true
        );
        arrangeCommon(urbanFarmer, posting, preference, 4);
        when(applicationRepository.findByJobPostingIdAndUrbanFarmerId(
                POSTING_ID,
                USER_ID
        )).thenReturn(Optional.empty());
        when(applicationRepository.saveAndFlush(any(JobApplication.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.apply(USER_ID, POSTING_ID);

        ArgumentCaptor<JobApplication> captor =
                ArgumentCaptor.forClass(JobApplication.class);
        verify(applicationRepository).saveAndFlush(captor.capture());
        JobApplication application = captor.getValue();
        assertThat(application.getPreferredRegionsSnapshot())
                .isEqualTo("CHEONGJU,CHUNGJU");
        assertThat(application.getAvailableDaysSnapshot())
                .isEqualTo("MONDAY,WEDNESDAY");
        assertThat(application.getPreferredStartDateSnapshot())
                .isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(application.getPreferredEndDateSnapshot())
                .isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(application.getAvailableWorkTypesSnapshot())
                .isEqualTo("HARVEST,SORTING");
        assertThat(application.getCanTravelSnapshot()).isTrue();
        assertThat(application.getExperienceCountSnapshot()).isEqualTo(4);
    }

    @Test
    void reapplyRefreshesSnapshotsFromLatestPreferenceAndProfile() {
        User urbanFarmer = urbanFarmer();
        JobPosting posting = openPosting();
        JobApplication existing = JobApplication.apply(
                posting,
                urbanFarmer,
                Instant.parse("2026-08-01T00:00:00Z"),
                "CHEONGJU",
                "MONDAY",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                "PLANTING",
                false,
                1
        );
        existing.withdraw(Instant.parse("2026-08-02T00:00:00Z"));
        UrbanFarmerWorkPreference latestPreference = preference(
                urbanFarmer,
                List.of(ChungbukCityCounty.CHUNGJU, ChungbukCityCounty.JECHEON),
                List.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
                List.of("HARVEST", "PACKING"),
                LocalDate.of(2026, 9, 15),
                LocalDate.of(2026, 11, 30),
                true
        );
        arrangeCommon(urbanFarmer, posting, latestPreference, 7);
        when(applicationRepository.findByJobPostingIdAndUrbanFarmerId(
                POSTING_ID,
                USER_ID
        )).thenReturn(Optional.of(existing));

        service.apply(USER_ID, POSTING_ID);

        assertThat(existing.getStatus())
                .isEqualTo(JobApplication.ApplicationStatus.APPLIED);
        assertThat(existing.getPreferredRegionsSnapshot())
                .isEqualTo("CHUNGJU,JECHEON");
        assertThat(existing.getAvailableDaysSnapshot())
                .isEqualTo("TUESDAY,THURSDAY");
        assertThat(existing.getPreferredStartDateSnapshot())
                .isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(existing.getPreferredEndDateSnapshot())
                .isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(existing.getAvailableWorkTypesSnapshot())
                .isEqualTo("HARVEST,PACKING");
        assertThat(existing.getCanTravelSnapshot()).isTrue();
        assertThat(existing.getExperienceCountSnapshot()).isEqualTo(7);
    }

    @Test
    void myApplicationsUseStableNewestFirstOrdering() {
        User urbanFarmer = urbanFarmer();
        var pageable = PageRequest.of(1, 10, Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
        ));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(urbanFarmer));
        when(applicationRepository.findByUrbanFarmerId(USER_ID, pageable))
                .thenReturn(Page.empty(pageable));

        service.getMine(USER_ID, 1, 10);

        verify(applicationRepository).findByUrbanFarmerId(USER_ID, pageable);
    }

    private void arrangeCommon(
            User urbanFarmer,
            JobPosting posting,
            UrbanFarmerWorkPreference preference,
            int experienceCount
    ) {
        when(postingRepository.findByIdForUpdate(POSTING_ID))
                .thenReturn(Optional.of(posting));
        when(userRepository.findByIdForUpdate(USER_ID))
                .thenReturn(Optional.of(urbanFarmer));
        when(preferenceRepository.findByUrbanFarmerId(USER_ID))
                .thenReturn(Optional.of(preference));
        when(profileRepository.findByUrbanFarmerId(USER_ID))
                .thenReturn(Optional.of(UrbanFarmerProfile.create(
                        urbanFarmer,
                        false,
                        experienceCount,
                        null
                )));
    }

    private UrbanFarmerWorkPreference preference(
            User urbanFarmer,
            List<ChungbukCityCounty> regions,
            List<DayOfWeek> days,
            List<String> workTypes,
            LocalDate startDate,
            LocalDate endDate,
            boolean canTravel
    ) {
        return UrbanFarmerWorkPreference.create(
                urbanFarmer,
                regions,
                days,
                workTypes,
                startDate,
                endDate,
                canTravel,
                null
        );
    }

    private User urbanFarmer() {
        User user = User.register(
                "urban_1",
                "encoded",
                "Urban Farmer",
                User.UserType.URBAN_FARMER
        );
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

    private JobPosting openPosting() {
        JobPosting posting = mock(JobPosting.class);
        FarmProfile farm = mock(FarmProfile.class);
        when(posting.isAcceptingApplications(
                any(LocalDate.class),
                any(LocalTime.class)
        )).thenReturn(true);
        when(posting.isVisibleToUrbanFarmers()).thenReturn(true);
        when(posting.getFarmProfile()).thenReturn(farm);
        when(posting.getId()).thenReturn(POSTING_ID);
        when(posting.getWageUnit()).thenReturn(JobPosting.WageUnit.DAILY);
        return posting;
    }
}
