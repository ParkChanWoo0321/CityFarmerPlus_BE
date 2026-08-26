package chungbuk.cityfarmerplus.jobposting.service;

import chungbuk.cityfarmerplus.application.repository.JobApplicationRepository;
import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import chungbuk.cityfarmerplus.jobposting.dto.FarmJobPostingDisplayStatus;
import chungbuk.cityfarmerplus.jobposting.dto.JobPostingResponse;
import chungbuk.cityfarmerplus.jobposting.dto.JobPostingUpsertRequest;
import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;
import chungbuk.cityfarmerplus.jobposting.repository.JobPostingRepository;
import chungbuk.cityfarmerplus.jobposting.repository.JobPostingReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FarmJobPostingServiceTest {

    @Mock
    private JobPostingRepository postingRepository;
    @Mock
    private JobPostingAccessService accessService;
    @Mock
    private JobApplicationRepository applicationRepository;
    @Mock
    private JobPostingReviewRepository reviewRepository;
    @Mock
    private JobPostingResponseAssembler responseAssembler;
    @Mock
    private JobPostingScheduleValidator scheduleValidator;

    private FarmJobPostingService service;

    @BeforeEach
    void setUp() {
        service = new FarmJobPostingService(
                postingRepository,
                accessService,
                applicationRepository,
                reviewRepository,
                responseAssembler,
                scheduleValidator
        );
    }

    @Test
    void createsAndSubmitsPostingForReviewInOneTransactionFlow() {
        FarmProfile farm = org.mockito.Mockito.mock(FarmProfile.class);
        when(farm.getStatus()).thenReturn(FarmProfile.FarmProfileStatus.APPROVED);
        when(accessService.requireApprovedFarmForUpdate(1L)).thenReturn(farm);
        when(postingRepository.saveAndFlush(any(JobPosting.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        JobPostingResponse expected = org.mockito.Mockito.mock(JobPostingResponse.class);
        when(responseAssembler.assemble(any(JobPosting.class))).thenReturn(expected);

        JobPostingResponse response = service.create(1L, request(), true);

        ArgumentCaptor<JobPosting> captor = ArgumentCaptor.forClass(JobPosting.class);
        verify(postingRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getStatus())
                .isEqualTo(JobPosting.JobPostingStatus.PENDING_REVIEW);
        assertThat(captor.getValue().getReviewRequestedAt()).isNotNull();
        assertThat(response).isSameAs(expected);
    }

    @Test
    void readsRejectedFilterWithoutRequiringApprovedFarm() {
        FarmProfile farm = org.mockito.Mockito.mock(FarmProfile.class);
        when(accessService.requireFarmProfile(1L)).thenReturn(farm);
        when(postingRepository.findAll(
                any(Specification.class),
                any(Pageable.class)
        )).thenAnswer(invocation -> new PageImpl<>(
                List.of(),
                invocation.getArgument(1),
                0
        ));
        when(responseAssembler.assembleAll(List.of())).thenReturn(List.of());

        var response = service.getMine(
                1L,
                FarmJobPostingDisplayStatus.REJECTED,
                0,
                20
        );

        assertThat(response.content()).isEmpty();
        verify(accessService).requireFarmProfile(1L);
        verify(accessService, never()).requireApprovedFarm(1L);
        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);
        verify(postingRepository).findAll(
                any(Specification.class),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getSort().toList())
                .containsExactly(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                );
    }

    @Test
    void validatesScheduleAgainWhenExistingDraftIsSubmitted() {
        JobPosting posting = org.mockito.Mockito.mock(JobPosting.class);
        FarmProfile farm = org.mockito.Mockito.mock(FarmProfile.class);
        User owner = org.mockito.Mockito.mock(User.class);
        LocalDate workDate = LocalDate.of(2026, 8, 20);
        LocalTime startTime = LocalTime.of(9, 0);
        LocalTime endTime = LocalTime.of(16, 0);
        when(posting.getFarmProfile()).thenReturn(farm);
        when(farm.getOwner()).thenReturn(owner);
        when(owner.getId()).thenReturn(1L);
        when(posting.getWorkDate()).thenReturn(workDate);
        when(posting.getStartTime()).thenReturn(startTime);
        when(posting.getEndTime()).thenReturn(endTime);
        when(postingRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(posting));
        JobPostingResponse expected = org.mockito.Mockito.mock(JobPostingResponse.class);
        when(responseAssembler.assemble(posting)).thenReturn(expected);

        JobPostingResponse response = service.submitReview(1L, 10L);

        verify(scheduleValidator).validate(workDate, startTime, endTime);
        verify(posting).submitForReview(any());
        assertThat(response).isSameAs(expected);
    }

    @Test
    void rejectsApplicantPreferenceUpdateAfterWorkHasStarted() {
        JobPosting posting = org.mockito.Mockito.mock(JobPosting.class);
        FarmProfile farm = org.mockito.Mockito.mock(FarmProfile.class);
        User owner = org.mockito.Mockito.mock(User.class);
        when(posting.getFarmProfile()).thenReturn(farm);
        when(farm.getOwner()).thenReturn(owner);
        when(owner.getId()).thenReturn(1L);
        when(postingRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(posting));
        when(posting.isAcceptingApplications(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.updateApplicantPreference(
                1L,
                10L,
                "초보자 우대"
        )).hasMessageContaining("작업 시작 전");

        verify(posting, never()).updateApplicantPreference(any());
    }

    @Test
    void deletesReviewHistoryBeforeDeletingDraftPosting() {
        JobPosting posting = org.mockito.Mockito.mock(JobPosting.class);
        FarmProfile farm = org.mockito.Mockito.mock(FarmProfile.class);
        User owner = org.mockito.Mockito.mock(User.class);
        when(postingRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(posting));
        when(posting.getFarmProfile()).thenReturn(farm);
        when(farm.getOwner()).thenReturn(owner);
        when(owner.getId()).thenReturn(1L);
        when(posting.getStatus()).thenReturn(JobPosting.JobPostingStatus.DRAFT);

        service.deleteDraft(1L, 10L);

        var deletionOrder = inOrder(reviewRepository, postingRepository);
        deletionOrder.verify(reviewRepository).deleteAllByJobPostingId(10L);
        deletionOrder.verify(postingRepository).delete(posting);
    }

    private JobPostingUpsertRequest request() {
        return new JobPostingUpsertRequest(
                "감자",
                "수확",
                LocalDate.of(2026, 8, 20),
                LocalTime.of(9, 0),
                LocalTime.of(16, 0),
                3,
                "농장 입구",
                100_000,
                JobPosting.WageUnit.DAILY,
                "장갑, 모자",
                "농기계 주변 안전거리 유지",
                "함께 일해요",
                "초보자 환영",
                "감자 수확 도우미를 찾아요",
                "감자 수확 작업자를 모집합니다.",
                "농가 안내에 따라 작업해 주세요."
        );
    }
}
