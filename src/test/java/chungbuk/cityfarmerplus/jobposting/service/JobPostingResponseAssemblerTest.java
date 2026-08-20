package chungbuk.cityfarmerplus.jobposting.service;

import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import chungbuk.cityfarmerplus.jobposting.dto.FarmJobPostingDisplayStatus;
import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;
import chungbuk.cityfarmerplus.jobposting.entity.JobPostingReview;
import chungbuk.cityfarmerplus.jobposting.repository.JobPostingReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobPostingResponseAssemblerTest {

    @Mock
    private JobPostingReviewRepository reviewRepository;

    private JobPostingResponseAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new JobPostingResponseAssembler(reviewRepository);
    }

    @Test
    void exposesLatestRejectedReviewForFarmPostingCard() {
        JobPosting posting = posting(10L);
        when(posting.getReviewRequestedAt())
                .thenReturn(Instant.parse("2026-08-10T01:00:00Z"));
        JobPostingReview review = review(
                JobPostingReview.ReviewAction.REJECTED,
                "집결 장소를 보완해 주세요.",
                Instant.parse("2026-08-11T01:00:00Z")
        );
        when(reviewRepository.findFirstByJobPostingIdOrderByIdDesc(10L))
                .thenReturn(Optional.of(review));

        var response = assembler.assemble(posting);

        assertThat(response.latestReviewAction())
                .isEqualTo(JobPostingReview.ReviewAction.REJECTED);
        assertThat(response.displayStatus())
                .isEqualTo(FarmJobPostingDisplayStatus.REJECTED);
        assertThat(response.latestReviewReason()).isEqualTo("집결 장소를 보완해 주세요.");
        assertThat(response.latestReviewedAt())
                .isEqualTo(Instant.parse("2026-08-11T01:00:00Z"));
    }

    @Test
    void batchAssemblyUsesFirstNewestReviewForEachPosting() {
        JobPosting posting = posting(10L);
        when(posting.getReviewRequestedAt())
                .thenReturn(Instant.parse("2026-08-10T01:00:00Z"));
        JobPostingReview newest = review(
                JobPostingReview.ReviewAction.REJECTED,
                "최신 반려 사유",
                Instant.parse("2026-08-11T01:00:00Z")
        );
        when(newest.getJobPosting()).thenReturn(posting);
        JobPostingReview older = mock(JobPostingReview.class);
        when(older.getJobPosting()).thenReturn(posting);
        when(reviewRepository.findAllNewestFirstByJobPostingIds(List.of(10L)))
                .thenReturn(List.of(newest, older));

        var responses = assembler.assembleAll(List.of(posting));

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).latestReviewAction())
                .isEqualTo(JobPostingReview.ReviewAction.REJECTED);
        assertThat(responses.get(0).latestReviewReason()).isEqualTo("최신 반려 사유");
    }

    @Test
    void oldRejectionIsNotDisplayedAfterNewerReviewRequestWasWithdrawn() {
        JobPosting posting = posting(10L);
        when(posting.getReviewRequestedAt())
                .thenReturn(Instant.parse("2026-08-12T01:00:00Z"));
        JobPostingReview oldRejection = review(
                JobPostingReview.ReviewAction.REJECTED,
                "과거 반려 사유",
                Instant.parse("2026-08-11T01:00:00Z")
        );
        when(reviewRepository.findFirstByJobPostingIdOrderByIdDesc(10L))
                .thenReturn(Optional.of(oldRejection));

        var response = assembler.assemble(posting);

        assertThat(response.displayStatus())
                .isEqualTo(FarmJobPostingDisplayStatus.DRAFT);
    }

    @Test
    void expiredOpenPostingIsDisplayedAsClosed() {
        JobPosting posting = posting(10L);
        when(posting.getStatus()).thenReturn(JobPosting.JobPostingStatus.OPEN);
        when(posting.getWorkDate()).thenReturn(LocalDate.now().minusDays(1));
        when(posting.getStartTime()).thenReturn(LocalTime.NOON);
        when(reviewRepository.findFirstByJobPostingIdOrderByIdDesc(10L))
                .thenReturn(Optional.empty());

        var response = assembler.assemble(posting);

        assertThat(response.displayStatus())
                .isEqualTo(FarmJobPostingDisplayStatus.CLOSED);
    }

    private JobPosting posting(Long id) {
        JobPosting posting = mock(JobPosting.class);
        FarmProfile farm = mock(FarmProfile.class);
        when(posting.getId()).thenReturn(id);
        when(posting.getFarmProfile()).thenReturn(farm);
        when(posting.getStatus()).thenReturn(JobPosting.JobPostingStatus.DRAFT);
        return posting;
    }

    private JobPostingReview review(
            JobPostingReview.ReviewAction action,
            String reason,
            Instant createdAt
    ) {
        JobPostingReview review = mock(JobPostingReview.class);
        when(review.getAction()).thenReturn(action);
        when(review.getReason()).thenReturn(reason);
        when(review.getCreatedAt()).thenReturn(createdAt);
        return review;
    }
}
