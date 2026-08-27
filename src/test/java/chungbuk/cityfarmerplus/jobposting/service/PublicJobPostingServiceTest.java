package chungbuk.cityfarmerplus.jobposting.service;

import chungbuk.cityfarmerplus.application.entity.JobApplication;
import chungbuk.cityfarmerplus.application.repository.JobApplicationRepository;
import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.common.exception.DomainException;
import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import chungbuk.cityfarmerplus.jobposting.dto.PublicRecruitmentStatus;
import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;
import chungbuk.cityfarmerplus.jobposting.exception.JobPostingException;
import chungbuk.cityfarmerplus.jobposting.repository.JobPostingRepository;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicJobPostingServiceTest {

    @Mock
    private JobPostingRepository postingRepository;
    @Mock
    private JobApplicationRepository applicationRepository;

    private PublicJobPostingService service;

    @BeforeEach
    void setUp() {
        service = new PublicJobPostingService(
                postingRepository,
                applicationRepository
        );
    }

    @Test
    void enrichesPostingPageWithMyApplicationUsingOneBatchQuery() {
        JobPosting posting = posting(10L, JobPosting.JobPostingStatus.OPEN, true);
        JobApplication application = mock(JobApplication.class);
        when(application.getId()).thenReturn(30L);
        when(application.getStatus())
                .thenReturn(JobApplication.ApplicationStatus.APPLIED);
        when(application.getJobPosting()).thenReturn(posting);
        when(postingRepository.findAll(
                any(Specification.class),
                any(Pageable.class)
        )).thenAnswer(invocation -> new PageImpl<>(
                List.of(posting),
                invocation.getArgument(1),
                1
        ));
        when(applicationRepository.findAllByJobPostingIdInAndUrbanFarmerId(
                List.of(10L),
                20L
        )).thenReturn(List.of(application));

        var response = service.getPostings(
                20L,
                null,
                null,
                "potato",
                null,
                null,
                null,
                PublicRecruitmentStatus.OPEN,
                0,
                20
        );

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).recruitmentStatus())
                .isEqualTo(PublicRecruitmentStatus.OPEN);
        assertThat(response.content().get(0).acceptingApplications()).isTrue();
        assertThat(response.content().get(0).myApplication().applicationId())
                .isEqualTo(30L);
        verify(applicationRepository)
                .findAllByJobPostingIdInAndUrbanFarmerId(List.of(10L), 20L);
    }

    @Test
    void allStatusUsesCriteriaOrderingSoOpenPostingsComeFirst() {
        when(postingRepository.findAll(
                any(Specification.class),
                any(Pageable.class)
        )).thenAnswer(invocation -> {
            Pageable pageable = invocation.getArgument(1);
            assertThat(pageable.getSort().isUnsorted()).isTrue();
            return new PageImpl<>(List.of(), pageable, 0);
        });

        service.getPostings(
                20L,
                null,
                null,
                null,
                null,
                null,
                null,
                PublicRecruitmentStatus.ALL,
                0,
                20
        );

        verify(applicationRepository, never())
                .findAllByJobPostingIdInAndUrbanFarmerId(any(), any());
    }

    @Test
    void openStatusUsesStableChronologicalOrdering() {
        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);
        when(postingRepository.findAll(
                any(Specification.class),
                any(Pageable.class)
        )).thenAnswer(invocation -> new PageImpl<>(
                List.of(),
                invocation.getArgument(1),
                0
        ));

        service.getPostings(
                20L,
                null,
                null,
                null,
                null,
                null,
                null,
                PublicRecruitmentStatus.OPEN,
                0,
                20
        );

        verify(postingRepository).findAll(
                any(Specification.class),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getSort().toList())
                .containsExactly(
                        Sort.Order.asc("workDate"),
                        Sort.Order.asc("startTime"),
                        Sort.Order.desc("approvedAt"),
                        Sort.Order.asc("id")
                );
    }

    @Test
    void includeClosedReadsPreviouslyApprovedClosedPosting() {
        JobPosting posting = posting(10L, JobPosting.JobPostingStatus.CLOSED, false);
        when(postingRepository.findById(10L)).thenReturn(Optional.of(posting));
        when(applicationRepository.findByJobPostingIdAndUrbanFarmerId(10L, 20L))
                .thenReturn(Optional.empty());

        var response = service.getPosting(20L, 10L, true);

        assertThat(response.recruitmentStatus())
                .isEqualTo(PublicRecruitmentStatus.CLOSED);
        assertThat(response.acceptingApplications()).isFalse();
    }

    @Test
    void closedPostingRemainsHiddenWithoutExplicitFlag() {
        JobPosting posting = posting(10L, JobPosting.JobPostingStatus.CLOSED, false);
        when(postingRepository.findById(10L)).thenReturn(Optional.of(posting));

        assertThatThrownBy(() -> service.getPosting(20L, 10L, false))
                .isInstanceOf(JobPostingException.class);

        verify(applicationRepository, never())
                .findByJobPostingIdAndUrbanFarmerId(any(), any());
    }

    @Test
    void cancelledPostingIsNeverPublicEvenWhenClosedIsIncluded() {
        JobPosting posting = posting(
                10L,
                JobPosting.JobPostingStatus.CANCELLED,
                false
        );
        when(postingRepository.findById(10L)).thenReturn(Optional.of(posting));

        assertThatThrownBy(() -> service.getPosting(20L, 10L, true))
                .isInstanceOf(JobPostingException.class);
    }

    @Test
    void postingWithoutApprovalHistoryIsNeverPublic() {
        JobPosting posting = posting(10L, JobPosting.JobPostingStatus.OPEN, true);
        when(posting.getApprovedAt()).thenReturn(null);
        when(postingRepository.findById(10L)).thenReturn(Optional.of(posting));

        assertThatThrownBy(() -> service.getPosting(20L, 10L, true))
                .isInstanceOf(JobPostingException.class);

        verify(applicationRepository, never())
                .findByJobPostingIdAndUrbanFarmerId(any(), any());
    }

    @Test
    void postingFromUnapprovedFarmRemainsPublicAfterPostingApproval() {
        JobPosting posting = posting(10L, JobPosting.JobPostingStatus.OPEN, true);
        lenient().when(posting.getFarmProfile().getStatus())
                .thenReturn(FarmProfile.FarmProfileStatus.PENDING_REVIEW);
        when(postingRepository.findById(10L)).thenReturn(Optional.of(posting));
        when(applicationRepository.findByJobPostingIdAndUrbanFarmerId(10L, 20L))
                .thenReturn(Optional.empty());

        var response = service.getPosting(20L, 10L, true);

        assertThat(response.acceptingApplications()).isTrue();
        verify(applicationRepository)
                .findByJobPostingIdAndUrbanFarmerId(10L, 20L);
    }

    @Test
    void postingFromInactiveFarmOwnerIsNeverPublic() {
        JobPosting posting = posting(10L, JobPosting.JobPostingStatus.OPEN, true);
        when(posting.getFarmProfile().getOwner().isActive()).thenReturn(false);
        when(postingRepository.findById(10L)).thenReturn(Optional.of(posting));

        assertThatThrownBy(() -> service.getPosting(20L, 10L, true))
                .isInstanceOf(JobPostingException.class);

        verify(applicationRepository, never())
                .findByJobPostingIdAndUrbanFarmerId(any(), any());
    }

    @Test
    void rejectsReversedDateRangeBeforeRepositoryQuery() {
        LocalDate from = LocalDate.of(2026, 8, 20);

        assertThatThrownBy(() -> service.getPostings(
                20L,
                null,
                null,
                null,
                from,
                from.minusDays(1),
                null,
                PublicRecruitmentStatus.OPEN,
                0,
                20
        )).isInstanceOfSatisfying(DomainException.class, exception -> {
            assertThat(exception.getStatus()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
            assertThat(exception.getCode()).isEqualTo("INVALID_JOB_POSTING_DATE_RANGE");
        });

        verify(postingRepository, never()).findAll(
                any(Specification.class),
                any(Pageable.class)
        );
    }

    private JobPosting posting(
            Long id,
            JobPosting.JobPostingStatus status,
            boolean acceptingApplications
    ) {
        JobPosting posting = mock(JobPosting.class);
        FarmProfile farm = mock(FarmProfile.class);
        User owner = mock(User.class);
        lenient().when(posting.getId()).thenReturn(id);
        lenient().when(posting.getStatus()).thenReturn(status);
        lenient().when(posting.getApprovedAt())
                .thenReturn(Instant.parse("2026-08-01T00:00:00Z"));
        lenient().when(posting.getFarmProfile()).thenReturn(farm);
        lenient().when(posting.isAcceptingApplications(
                        any(LocalDate.class),
                        any(LocalTime.class)
                ))
                .thenReturn(acceptingApplications);
        lenient().when(farm.getStatus())
                .thenReturn(FarmProfile.FarmProfileStatus.APPROVED);
        lenient().when(farm.getOwner()).thenReturn(owner);
        lenient().when(owner.isActive()).thenReturn(true);
        if (status == JobPosting.JobPostingStatus.OPEN && !acceptingApplications) {
            lenient().when(posting.getWorkDate())
                    .thenReturn(LocalDate.of(2000, 1, 1));
            lenient().when(posting.getStartTime()).thenReturn(LocalTime.NOON);
        }
        return posting;
    }
}
