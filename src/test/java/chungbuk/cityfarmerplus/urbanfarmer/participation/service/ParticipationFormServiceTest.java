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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParticipationFormServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-13T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private ParticipationApplicationRepository applicationRepository;
    @Mock
    private UrbanFarmerProfileRepository profileRepository;
    @Mock
    private UrbanFarmerWorkPreferenceRepository preferenceRepository;
    @Mock
    private UserRoleAccessService accessService;

    private ParticipationFormService service;
    private User urbanFarmer;

    @BeforeEach
    void setUp() {
        service = new ParticipationFormService(
                applicationRepository,
                profileRepository,
                preferenceRepository,
                accessService,
                CLOCK
        );
        urbanFarmer = user(1L, User.UserType.URBAN_FARMER);
    }

    @Test
    void submitsNewFormAtomicallyAfterTakingLocksInDeterministicOrder() {
        stubLocks(Optional.empty(), Optional.empty(), Optional.empty());

        ParticipationFormResponse response = service.submit(1L, 2026, request());

        assertThat(response.status())
                .isEqualTo(ParticipationFormResponse.ParticipationFormStatus.SUBMITTED);
        assertThat(response.submittedAt()).isEqualTo(NOW);
        assertThat(response.experienceCount()).isEqualTo(3);
        assertThat(response.preferredStartDate()).isEqualTo(LocalDate.of(2026, 8, 20));
        verify(profileRepository).save(org.mockito.ArgumentMatchers.any());
        verify(preferenceRepository).save(org.mockito.ArgumentMatchers.any());
        verify(applicationRepository).save(org.mockito.ArgumentMatchers.any());
        verify(applicationRepository).flush();

        InOrder lockOrder = inOrder(
                accessService,
                applicationRepository,
                profileRepository,
                preferenceRepository
        );
        lockOrder.verify(accessService).requireUrbanFarmerForUpdate(1L);
        lockOrder.verify(applicationRepository)
                .findByUrbanFarmerIdAndProgramYearForUpdate(1L, 2026);
        lockOrder.verify(profileRepository).findByUrbanFarmerIdForUpdate(1L);
        lockOrder.verify(preferenceRepository).findByUrbanFarmerIdForUpdate(1L);
    }

    @Test
    void putOnSubmittedFormKeepsIdStatusAndOriginalSubmittedAt() {
        Instant firstSubmittedAt = Instant.parse("2026-08-01T00:00:00Z");
        ParticipationApplication application = draft(false, "before");
        ReflectionTestUtils.setField(application, "id", 20L);
        ReflectionTestUtils.setField(application, "version", 4L);
        application.submit(firstSubmittedAt);
        UrbanFarmerProfile profile = profile(false, 1, "before");
        ReflectionTestUtils.setField(profile, "id", 30L);
        ReflectionTestUtils.setField(profile, "version", 2L);
        UrbanFarmerWorkPreference preference = preference();
        ReflectionTestUtils.setField(preference, "id", 40L);
        ReflectionTestUtils.setField(preference, "version", 3L);
        stubLocks(
                Optional.of(application),
                Optional.of(profile),
                Optional.of(preference)
        );

        ParticipationFormResponse response = service.save(
                1L,
                2026,
                request(4L, 2L, 3L)
        );

        assertThat(response.applicationId()).isEqualTo(20L);
        assertThat(response.status())
                .isEqualTo(ParticipationFormResponse.ParticipationFormStatus.SUBMITTED);
        assertThat(response.submittedAt()).isEqualTo(firstSubmittedAt);
        assertThat(response.applicationNote()).isEqualTo("평일 근무 희망");
        assertThat(response.experienceCount()).isEqualTo(3);
    }

    @Test
    void rejectedFormIsResubmittedWithSameIdAndFreshReviewState() {
        ParticipationApplication application = draft(false, "before");
        ReflectionTestUtils.setField(application, "id", 20L);
        application.submit(Instant.parse("2026-08-01T00:00:00Z"));
        application.reject(
                user(9L, User.UserType.CENTER_ADMIN),
                "서류를 보완해 주세요.",
                Instant.parse("2026-08-02T00:00:00Z")
        );
        stubLocks(Optional.of(application), Optional.empty(), Optional.empty());

        ParticipationFormResponse response = service.submit(1L, 2026, request());

        assertThat(response.applicationId()).isEqualTo(20L);
        assertThat(response.status())
                .isEqualTo(ParticipationFormResponse.ParticipationFormStatus.SUBMITTED);
        assertThat(response.submittedAt()).isEqualTo(NOW);
        assertThat(response.rejectionReason()).isNull();
        assertThat(response.reviewedAt()).isNull();
        assertThat(response.reviewedByUserId()).isNull();
    }

    @Test
    void approvedFormAllowsExperienceAndPreferenceChangesOnly() {
        ParticipationApplication application = approved(false, "fixed note");
        UrbanFarmerProfile profile = profile(false, 1, "before");
        UrbanFarmerWorkPreference preference = preference();
        stubLocks(
                Optional.of(application),
                Optional.of(profile),
                Optional.of(preference)
        );
        ParticipationFormRequest request = requestWithLockedFields(false, "fixed note");

        ParticipationFormResponse response = service.save(1L, 2026, request);

        assertThat(response.status())
                .isEqualTo(ParticipationFormResponse.ParticipationFormStatus.APPROVED);
        assertThat(response.nextAction())
                .isEqualTo(ParticipationFormResponse.NextAction.SAVE_APPROVED_PREFERENCES);
        assertThat(response.editableFields())
                .contains("experienceCount", "preferredRegions")
                .doesNotContain("agriculturalBusinessRegistered", "applicationNote");
        assertThat(profile.getExperienceCount()).isEqualTo(3);
        assertThat(profile.isAgriculturalBusinessRegistered()).isFalse();
        assertThat(preference.getPreferredEndDate()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    void approvedFormRejectsChangesToLockedApplicationFields() {
        ParticipationApplication application = approved(false, "fixed note");
        UrbanFarmerProfile profile = profile(false, 1, "before");
        UrbanFarmerWorkPreference preference = preference();
        stubLocks(
                Optional.of(application),
                Optional.of(profile),
                Optional.of(preference)
        );

        assertThatThrownBy(() -> service.save(
                1L,
                2026,
                requestWithLockedFields(true, "changed")
        )).isInstanceOfSatisfying(DomainException.class, exception ->
                assertThat(exception.getCode())
                        .isEqualTo("APPROVED_PARTICIPATION_FIELDS_LOCKED"));

        verify(profileRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(preferenceRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void cancelledFormCannotBeSubmittedAgain() {
        ParticipationApplication application = draft(false, null);
        application.cancel(Instant.parse("2026-08-01T00:00:00Z"));
        stubLocks(Optional.of(application), Optional.empty(), Optional.empty());

        assertThatThrownBy(() -> service.submit(1L, 2026, request()))
                .isInstanceOfSatisfying(DomainException.class, exception ->
                        assertThat(exception.getCode())
                                .isEqualTo("INVALID_PARTICIPATION_FORM_STATUS"));
    }

    @Test
    void staleExpectedVersionIsRejectedBeforeMutation() {
        ParticipationApplication application = draft(false, null);
        ReflectionTestUtils.setField(application, "version", 5L);
        stubLocks(Optional.of(application), Optional.empty(), Optional.empty());

        assertThatThrownBy(() -> service.save(
                1L,
                2026,
                request(4L, null, null)
        )).isInstanceOfSatisfying(DomainException.class, exception ->
                assertThat(exception.getCode())
                        .isEqualTo("PARTICIPATION_FORM_VERSION_CONFLICT"));

        verify(applicationRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void directServiceCallRejectsCommaDelimitedWorkType() {
        stubLocks(Optional.empty(), Optional.empty(), Optional.empty());

        assertThatThrownBy(() -> service.save(
                1L,
                2026,
                requestWithWorkTypes(List.of("수확,선별"))
        )).isInstanceOfSatisfying(DomainException.class, exception -> {
            assertThat(exception.getStatus().value()).isEqualTo(400);
            assertThat(exception.getCode()).isEqualTo("INVALID_WORK_TYPE");
        });

        verify(profileRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(preferenceRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(applicationRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private void stubLocks(
            Optional<ParticipationApplication> application,
            Optional<UrbanFarmerProfile> profile,
            Optional<UrbanFarmerWorkPreference> preference
    ) {
        when(accessService.requireUrbanFarmerForUpdate(1L)).thenReturn(urbanFarmer);
        when(applicationRepository.findByUrbanFarmerIdAndProgramYearForUpdate(1L, 2026))
                .thenReturn(application);
        when(profileRepository.findByUrbanFarmerIdForUpdate(1L)).thenReturn(profile);
        when(preferenceRepository.findByUrbanFarmerIdForUpdate(1L)).thenReturn(preference);
    }

    private ParticipationApplication draft(boolean registered, String note) {
        return ParticipationApplication.createDraft(
                urbanFarmer,
                2026,
                registered,
                note
        );
    }

    private ParticipationApplication approved(boolean registered, String note) {
        ParticipationApplication application = draft(registered, note);
        application.submit(Instant.parse("2026-08-01T00:00:00Z"));
        application.approve(
                user(9L, User.UserType.CENTER_ADMIN),
                Instant.parse("2026-08-02T00:00:00Z")
        );
        return application;
    }

    private UrbanFarmerProfile profile(boolean registered, int experience, String notes) {
        return UrbanFarmerProfile.create(urbanFarmer, registered, experience, notes);
    }

    private UrbanFarmerWorkPreference preference() {
        return UrbanFarmerWorkPreference.create(
                urbanFarmer,
                List.of(ChungbukCityCounty.CHUNGJU),
                List.of(DayOfWeek.MONDAY),
                List.of("수확"),
                LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 31),
                true,
                null
        );
    }

    private ParticipationFormRequest request() {
        return request(null, null, null);
    }

    private ParticipationFormRequest request(
            Long applicationVersion,
            Long profileVersion,
            Long preferenceVersion
    ) {
        return new ParticipationFormRequest(
                true,
                3,
                "감자 수확 경험",
                List.of(ChungbukCityCounty.CHUNGJU),
                List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                List.of("수확", "선별"),
                LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 9, 30),
                true,
                "충주 지역 선호",
                "평일 근무 희망",
                applicationVersion,
                profileVersion,
                preferenceVersion
        );
    }

    private ParticipationFormRequest requestWithLockedFields(
            boolean registered,
            String applicationNote
    ) {
        ParticipationFormRequest base = request();
        return new ParticipationFormRequest(
                registered,
                base.experienceCount(),
                base.experienceNotes(),
                base.preferredRegions(),
                base.availableDays(),
                base.availableWorkTypes(),
                base.preferredStartDate(),
                base.preferredEndDate(),
                base.canTravel(),
                base.workPreferenceNotes(),
                applicationNote,
                null,
                null,
                null
        );
    }

    private ParticipationFormRequest requestWithWorkTypes(List<String> workTypes) {
        ParticipationFormRequest base = request();
        return new ParticipationFormRequest(
                base.agriculturalBusinessRegistered(),
                base.experienceCount(),
                base.experienceNotes(),
                base.preferredRegions(),
                base.availableDays(),
                workTypes,
                base.preferredStartDate(),
                base.preferredEndDate(),
                base.canTravel(),
                base.workPreferenceNotes(),
                base.applicationNote(),
                base.expectedApplicationVersion(),
                base.expectedProfileVersion(),
                base.expectedWorkPreferenceVersion()
        );
    }

    private User user(Long id, User.UserType type) {
        User user = type == User.UserType.CENTER_ADMIN
                ? User.registerCenterAdmin("admin_" + id, "encoded", "담당자")
                : User.register("urban_" + id, "encoded", "도시농부", type);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
