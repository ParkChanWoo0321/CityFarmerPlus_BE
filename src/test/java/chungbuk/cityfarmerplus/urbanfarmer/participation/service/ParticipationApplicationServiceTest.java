package chungbuk.cityfarmerplus.urbanfarmer.participation.service;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.common.exception.DomainException;
import chungbuk.cityfarmerplus.urbanfarmer.participation.dto.ParticipationApplicationRequest;
import chungbuk.cityfarmerplus.urbanfarmer.participation.dto.ParticipationApplicationUpdateRequest;
import chungbuk.cityfarmerplus.urbanfarmer.participation.entity.ParticipationApplication;
import chungbuk.cityfarmerplus.urbanfarmer.participation.repository.ParticipationApplicationRepository;
import chungbuk.cityfarmerplus.urbanfarmer.service.UserRoleAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParticipationApplicationServiceTest {

    @Mock
    private ParticipationApplicationRepository applicationRepository;

    @Mock
    private UserRoleAccessService accessService;

    private ParticipationApplicationService applicationService;

    @BeforeEach
    void setUp() {
        applicationService = new ParticipationApplicationService(
                applicationRepository,
                accessService
        );
    }

    @Test
    void createsNormalizedDraftForAuthenticatedUrbanFarmer() {
        User urbanFarmer = urbanFarmer(21L);
        when(accessService.requireUrbanFarmerForUpdate(21L)).thenReturn(urbanFarmer);
        when(applicationRepository.existsByUrbanFarmerIdAndProgramYear(21L, 2026))
                .thenReturn(false);
        when(applicationRepository.saveAndFlush(any(ParticipationApplication.class)))
                .thenAnswer(invocation -> persisted(invocation.getArgument(0), 100L));

        var response = applicationService.create(
                21L,
                new ParticipationApplicationRequest(2026, true, "  평일 참여 희망  ")
        );

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.urbanFarmerId()).isEqualTo(21L);
        assertThat(response.programYear()).isEqualTo(2026);
        assertThat(response.applicationNote()).isEqualTo("평일 참여 희망");
        assertThat(response.status())
                .isEqualTo(ParticipationApplication.ParticipationStatus.DRAFT);

        ArgumentCaptor<ParticipationApplication> captor =
                ArgumentCaptor.forClass(ParticipationApplication.class);
        verify(applicationRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getUrbanFarmer()).isSameAs(urbanFarmer);
    }

    @Test
    void duplicateProgramYearIsRejectedBeforePersistence() {
        when(accessService.requireUrbanFarmerForUpdate(21L))
                .thenReturn(urbanFarmer(21L));
        when(applicationRepository.existsByUrbanFarmerIdAndProgramYear(21L, 2026))
                .thenReturn(true);

        assertThatThrownBy(() -> applicationService.create(
                21L,
                new ParticipationApplicationRequest(2026, true, null)
        ))
                .isInstanceOf(DomainException.class)
                .extracting("code")
                .isEqualTo("PARTICIPATION_APPLICATION_ALREADY_EXISTS");

        verify(applicationRepository, never()).saveAndFlush(any());
    }

    @Test
    void detailLookupIsScopedToAuthenticatedOwner() {
        when(accessService.requireUrbanFarmer(21L)).thenReturn(urbanFarmer(21L));
        when(applicationRepository.findByIdAndUrbanFarmerId(300L, 21L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.getMine(21L, 300L))
                .isInstanceOf(DomainException.class)
                .extracting("code")
                .isEqualTo("PARTICIPATION_APPLICATION_NOT_FOUND");

        verify(applicationRepository).findByIdAndUrbanFarmerId(300L, 21L);
    }

    @Test
    void editingRejectedApplicationReturnsItToDraftAndClearsReviewState() {
        User owner = urbanFarmer(21L);
        ParticipationApplication application = rejectedApplication(owner, 100L);
        Instant flushedAt = Instant.parse("2026-08-15T03:00:00Z");
        ReflectionTestUtils.setField(application, "version", 2L);
        when(accessService.requireUrbanFarmerForUpdate(21L)).thenReturn(owner);
        when(applicationRepository.findByIdAndUrbanFarmerId(100L, 21L))
                .thenReturn(Optional.of(application));
        doAnswer(invocation -> {
            ReflectionTestUtils.setField(application, "version", 3L);
            ReflectionTestUtils.setField(application, "updatedAt", flushedAt);
            return null;
        }).when(applicationRepository).flush();

        var response = applicationService.update(
                21L,
                100L,
                new ParticipationApplicationUpdateRequest(true, "  서류 보완 완료  ")
        );

        assertThat(response.status())
                .isEqualTo(ParticipationApplication.ParticipationStatus.DRAFT);
        assertThat(response.applicationNote()).isEqualTo("서류 보완 완료");
        assertThat(response.rejectionReason()).isNull();
        assertThat(response.reviewedByUserId()).isNull();
        assertThat(response.reviewedAt()).isNull();
        assertThat(response.version()).isEqualTo(3L);
        assertThat(response.updatedAt()).isEqualTo(flushedAt);
        verify(applicationRepository).flush();
    }

    @Test
    void submitReturnsVersionAndUpdatedAtProducedByFlush() {
        User owner = urbanFarmer(21L);
        ParticipationApplication application = draftApplication(owner, 100L);
        Instant flushedAt = Instant.parse("2026-08-15T01:00:01Z");
        when(accessService.requireUrbanFarmerForUpdate(21L)).thenReturn(owner);
        when(applicationRepository.findByIdAndUrbanFarmerId(100L, 21L))
                .thenReturn(Optional.of(application));
        doAnswer(invocation -> {
            ReflectionTestUtils.setField(application, "version", 1L);
            ReflectionTestUtils.setField(application, "updatedAt", flushedAt);
            return null;
        }).when(applicationRepository).flush();

        var response = applicationService.submit(21L, 100L);

        assertThat(response.status())
                .isEqualTo(ParticipationApplication.ParticipationStatus.SUBMITTED);
        assertThat(response.version()).isEqualTo(1L);
        assertThat(response.updatedAt()).isEqualTo(flushedAt);
        verify(applicationRepository).flush();
    }

    @Test
    void cancelReturnsVersionAndUpdatedAtProducedByFlush() {
        User owner = urbanFarmer(21L);
        ParticipationApplication application = draftApplication(owner, 100L);
        application.submit(Instant.parse("2026-08-15T01:00:00Z"));
        Instant flushedAt = Instant.parse("2026-08-15T02:00:01Z");
        ReflectionTestUtils.setField(application, "version", 1L);
        when(accessService.requireUrbanFarmerForUpdate(21L)).thenReturn(owner);
        when(applicationRepository.findByIdAndUrbanFarmerId(100L, 21L))
                .thenReturn(Optional.of(application));
        doAnswer(invocation -> {
            ReflectionTestUtils.setField(application, "version", 2L);
            ReflectionTestUtils.setField(application, "updatedAt", flushedAt);
            return null;
        }).when(applicationRepository).flush();

        var response = applicationService.cancel(21L, 100L);

        assertThat(response.status())
                .isEqualTo(ParticipationApplication.ParticipationStatus.CANCELLED);
        assertThat(response.version()).isEqualTo(2L);
        assertThat(response.updatedAt()).isEqualTo(flushedAt);
        verify(applicationRepository).flush();
    }

    @Test
    void submittingAlreadySubmittedApplicationReturnsStatusConflict() {
        User owner = urbanFarmer(21L);
        ParticipationApplication application = draftApplication(owner, 100L);
        application.submit(Instant.parse("2026-08-15T01:00:00Z"));
        when(accessService.requireUrbanFarmerForUpdate(21L)).thenReturn(owner);
        when(applicationRepository.findByIdAndUrbanFarmerId(100L, 21L))
                .thenReturn(Optional.of(application));

        assertThatThrownBy(() -> applicationService.submit(21L, 100L))
                .isInstanceOf(DomainException.class)
                .extracting("code")
                .isEqualTo("INVALID_PARTICIPATION_STATUS");

        assertThat(application.getStatus())
                .isEqualTo(ParticipationApplication.ParticipationStatus.SUBMITTED);
    }

    @Test
    void deletingSubmittedApplicationIsRejectedAndKeepsTheEntity() {
        User owner = urbanFarmer(21L);
        ParticipationApplication application = draftApplication(owner, 100L);
        application.submit(Instant.parse("2026-08-15T01:00:00Z"));
        when(accessService.requireUrbanFarmerForUpdate(21L)).thenReturn(owner);
        when(applicationRepository.findByIdAndUrbanFarmerId(100L, 21L))
                .thenReturn(Optional.of(application));

        assertThatThrownBy(() -> applicationService.deleteDraft(21L, 100L))
                .isInstanceOf(DomainException.class)
                .extracting("code")
                .isEqualTo("INVALID_PARTICIPATION_STATUS");

        verify(applicationRepository, never()).delete(any());
    }

    private ParticipationApplication rejectedApplication(User owner, Long id) {
        ParticipationApplication application = draftApplication(owner, id);
        application.submit(Instant.parse("2026-08-15T01:00:00Z"));
        User reviewer = User.registerCenterAdmin(
                "center_admin",
                "encoded-password",
                "담당자"
        );
        ReflectionTestUtils.setField(reviewer, "id", 91L);
        application.reject(
                reviewer,
                "서류를 보완해 주세요.",
                Instant.parse("2026-08-15T02:00:00Z")
        );
        return application;
    }

    private ParticipationApplication draftApplication(User owner, Long id) {
        ParticipationApplication application = ParticipationApplication.createDraft(
                owner,
                2026,
                false,
                "초안"
        );
        return persisted(application, id);
    }

    private ParticipationApplication persisted(
            ParticipationApplication application,
            Long id
    ) {
        ReflectionTestUtils.setField(application, "id", id);
        ReflectionTestUtils.setField(
                application,
                "createdAt",
                Instant.parse("2026-08-15T00:00:00Z")
        );
        ReflectionTestUtils.setField(
                application,
                "updatedAt",
                Instant.parse("2026-08-15T00:00:00Z")
        );
        return application;
    }

    private User urbanFarmer(Long id) {
        User user = User.register(
                "urban_" + id,
                "encoded-password",
                "도시농부 " + id,
                User.UserType.URBAN_FARMER
        );
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
