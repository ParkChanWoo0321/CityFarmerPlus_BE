package chungbuk.cityfarmerplus.jobposting.service;

import chungbuk.cityfarmerplus.jobposting.dto.JobPostingResponse;
import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;
import chungbuk.cityfarmerplus.jobposting.entity.JobPostingReview;
import chungbuk.cityfarmerplus.jobposting.repository.JobPostingReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JobPostingResponseAssembler {

    private final JobPostingReviewRepository reviewRepository;

    public JobPostingResponse assemble(JobPosting posting) {
        JobPostingReview latestReview = reviewRepository
                .findFirstByJobPostingIdOrderByIdDesc(posting.getId())
                .orElse(null);
        return JobPostingResponse.from(posting, latestReview);
    }

    public List<JobPostingResponse> assembleAll(List<JobPosting> postings) {
        if (postings.isEmpty()) {
            return List.of();
        }

        List<Long> postingIds = postings.stream()
                .map(JobPosting::getId)
                .toList();
        Map<Long, JobPostingReview> latestByPostingId = new HashMap<>();
        reviewRepository.findAllNewestFirstByJobPostingIds(postingIds)
                .forEach(review -> latestByPostingId.putIfAbsent(
                        review.getJobPosting().getId(),
                        review
                ));

        return postings.stream()
                .map(posting -> JobPostingResponse.from(
                        posting,
                        latestByPostingId.get(posting.getId())
                ))
                .toList();
    }
}
