package chungbuk.cityfarmerplus.jobposting.service;

import chungbuk.cityfarmerplus.application.entity.JobApplication;
import chungbuk.cityfarmerplus.application.repository.JobApplicationRepository;
import chungbuk.cityfarmerplus.common.web.PageResponse;
import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import chungbuk.cityfarmerplus.jobposting.dto.FarmJobPostingDisplayStatus;
import chungbuk.cityfarmerplus.jobposting.dto.JobPostingResponse;
import chungbuk.cityfarmerplus.jobposting.dto.JobPostingUpsertRequest;
import chungbuk.cityfarmerplus.jobposting.dto.JobPostingReviewResponse;
import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;
import chungbuk.cityfarmerplus.jobposting.exception.JobPostingException;
import chungbuk.cityfarmerplus.jobposting.repository.JobPostingRepository;
import chungbuk.cityfarmerplus.jobposting.repository.JobPostingReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FarmJobPostingService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final JobPostingRepository jobPostingRepository;
    private final JobPostingAccessService accessService;
    private final JobApplicationRepository applicationRepository;
    private final JobPostingReviewRepository reviewRepository;
    private final JobPostingResponseAssembler responseAssembler;
    private final JobPostingScheduleValidator scheduleValidator;

    @Transactional
    public JobPostingResponse create(Long userId, JobPostingUpsertRequest request) {
        return create(userId, request, false);
    }

    @Transactional
    public JobPostingResponse create(
            Long userId,
            JobPostingUpsertRequest request,
            boolean submitForReview
    ) {
        FarmProfile farmProfile = accessService.requireFarmProfileForUpdate(userId);
        scheduleValidator.validate(
                request.workDate(),
                request.startTime(),
                request.endTime()
        );
        try {
            JobPosting posting = JobPosting.createDraft(farmProfile, request.toDetails());
            if (submitForReview) {
                posting.submitForReview(Instant.now());
            }
            return responseAssembler.assemble(jobPostingRepository.saveAndFlush(posting));
        } catch (IllegalArgumentException exception) {
            throw JobPostingException.invalidDetails(exception.getMessage());
        }
    }

    public PageResponse<JobPostingResponse> getMine(Long userId, int page, int size) {
        return getMine(userId, null, page, size);
    }

    public PageResponse<JobPostingResponse> getMine(
            Long userId,
            FarmJobPostingDisplayStatus displayStatus,
            int page,
            int size
    ) {
        accessService.requireFarmProfile(userId);
        var pageable = PageRequest.of(page, size, Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
        ));
        ZonedDateTime now = ZonedDateTime.now(SERVICE_ZONE);
        Specification<JobPosting> specification = Specification
                .where(JobPostingSpecifications.belongsToFarmOwner(userId))
                .and(displayStatusSpecification(
                        displayStatus,
                        now
                ));
        var postings = jobPostingRepository.findAll(specification, pageable);
        return new PageResponse<>(
                responseAssembler.assembleAll(postings.getContent()),
                postings.getNumber(),
                postings.getSize(),
                postings.getTotalElements(),
                postings.getTotalPages(),
                postings.hasNext()
        );
    }

    public JobPostingResponse getMine(Long userId, Long postingId) {
        accessService.requireFarmProfile(userId);
        return responseAssembler.assemble(getOwned(userId, postingId));
    }

    public List<JobPostingReviewResponse> getReviewHistory(
            Long userId,
            Long postingId
    ) {
        accessService.requireFarmProfile(userId);
        getOwned(userId, postingId);
        return reviewRepository.findByJobPostingIdOrderByCreatedAtDescIdDesc(postingId)
                .stream()
                .map(JobPostingReviewResponse::from)
                .toList();
    }

    @Transactional
    public JobPostingResponse update(
            Long userId,
            Long postingId,
            JobPostingUpsertRequest request
    ) {
        accessService.requireFarmProfileForUpdate(userId);
        scheduleValidator.validate(
                request.workDate(),
                request.startTime(),
                request.endTime()
        );
        JobPosting posting = getOwnedForUpdate(userId, postingId);
        try {
            posting.updateDraft(request.toDetails());
        } catch (IllegalStateException exception) {
            throw JobPostingException.invalidState(exception.getMessage());
        } catch (IllegalArgumentException exception) {
            throw JobPostingException.invalidDetails(exception.getMessage());
        }
        return responseAssembler.assemble(posting);
    }

    @Transactional
    public void deleteDraft(Long userId, Long postingId) {
        accessService.requireFarmProfileForUpdate(userId);
        JobPosting posting = getOwnedForUpdate(userId, postingId);
        if (posting.getStatus() != JobPosting.JobPostingStatus.DRAFT) {
            throw JobPostingException.invalidState("초안 상태의 공고만 삭제할 수 있습니다.");
        }
        reviewRepository.deleteAllByJobPostingId(postingId);
        jobPostingRepository.delete(posting);
    }

    @Transactional
    public JobPostingResponse submitReview(Long userId, Long postingId) {
        accessService.requireFarmProfileForUpdate(userId);
        JobPosting posting = getOwnedForUpdate(userId, postingId);
        scheduleValidator.validate(
                posting.getWorkDate(),
                posting.getStartTime(),
                posting.getEndTime()
        );
        transition(() -> posting.submitForReview(Instant.now()));
        return responseAssembler.assemble(posting);
    }

    @Transactional
    public JobPostingResponse withdrawReview(Long userId, Long postingId) {
        accessService.requireFarmProfileForUpdate(userId);
        JobPosting posting = getOwnedForUpdate(userId, postingId);
        transition(posting::withdrawReview);
        return responseAssembler.assemble(posting);
    }

    @Transactional
    public JobPostingResponse updateApplicantPreference(
            Long userId,
            Long postingId,
            String preference
    ) {
        accessService.requireFarmProfileForUpdate(userId);
        JobPosting posting = getOwnedForUpdate(userId, postingId);
        ZonedDateTime now = ZonedDateTime.now(SERVICE_ZONE);
        if (!posting.isAcceptingApplications(
                now.toLocalDate(),
                now.toLocalTime()
        )) {
            throw JobPostingException.invalidState(
                    "작업 시작 전 모집 중인 공고에서만 희망 지원자 조건을 수정할 수 있습니다."
            );
        }
        transition(() -> posting.updateApplicantPreference(trimToNull(preference)));
        return responseAssembler.assemble(posting);
    }

    @Transactional
    public JobPostingResponse cancel(Long userId, Long postingId) {
        accessService.requireFarmProfileForUpdate(userId);
        JobPosting posting = getOwnedForUpdate(userId, postingId);
        if (applicationRepository.existsByJobPostingIdAndStatus(
                postingId,
                JobApplication.ApplicationStatus.MATCHED
        ) || applicationRepository.existsByJobPostingIdAndStatus(
                postingId,
                JobApplication.ApplicationStatus.WORK_COMPLETED
        )) {
            throw JobPostingException.activeMatchesExist();
        }
        transition(() -> posting.cancel(Instant.now()));
        applicationRepository.findByJobPostingIdAndStatus(
                postingId,
                JobApplication.ApplicationStatus.APPLIED
        ).forEach(JobApplication::cancelWithPosting);
        return responseAssembler.assemble(posting);
    }

    private JobPosting getOwned(Long userId, Long postingId) {
        JobPosting posting = jobPostingRepository.findById(postingId)
                .orElseThrow(JobPostingException::notFound);
        if (!posting.getFarmProfile().getOwner().getId().equals(userId)) {
            throw JobPostingException.notOwner();
        }
        return posting;
    }

    private JobPosting getOwnedForUpdate(Long userId, Long postingId) {
        JobPosting posting = jobPostingRepository.findByIdForUpdate(postingId)
                .orElseThrow(JobPostingException::notFound);
        if (!posting.getFarmProfile().getOwner().getId().equals(userId)) {
            throw JobPostingException.notOwner();
        }
        return posting;
    }

    private void transition(Runnable transition) {
        try {
            transition.run();
        } catch (IllegalStateException exception) {
            throw JobPostingException.invalidState(exception.getMessage());
        }
    }

    private Specification<JobPosting> displayStatusSpecification(
            FarmJobPostingDisplayStatus displayStatus,
            ZonedDateTime now
    ) {
        if (displayStatus == null) {
            return null;
        }
        return switch (displayStatus) {
            case DRAFT -> Specification
                    .where(JobPostingSpecifications.hasStatus(
                            JobPosting.JobPostingStatus.DRAFT
                    ))
                    .and(JobPostingSpecifications.doesNotHaveCurrentRejection());
            case PENDING -> JobPostingSpecifications.hasStatus(
                    JobPosting.JobPostingStatus.PENDING_REVIEW
            );
            case APPROVED -> Specification
                    .where(JobPostingSpecifications.hasStatus(
                            JobPosting.JobPostingStatus.OPEN
                    ))
                    .and(JobPostingSpecifications.startsAfter(
                            now.toLocalDate(),
                            now.toLocalTime()
                    ));
            case CLOSED -> Specification.anyOf(
                    JobPostingSpecifications.hasStatusIn(List.of(
                            JobPosting.JobPostingStatus.CLOSED,
                            JobPosting.JobPostingStatus.WORK_COMPLETED
                    )),
                    Specification
                            .where(JobPostingSpecifications.hasStatus(
                                    JobPosting.JobPostingStatus.OPEN
                            ))
                            .and(JobPostingSpecifications.startsAtOrBefore(
                                    now.toLocalDate(),
                                    now.toLocalTime()
                            ))
            );
            case REJECTED -> Specification
                    .where(JobPostingSpecifications.hasStatus(
                            JobPosting.JobPostingStatus.DRAFT
                    ))
                    .and(JobPostingSpecifications.hasCurrentRejection());
            case CANCELLED -> JobPostingSpecifications.hasStatus(
                    JobPosting.JobPostingStatus.CANCELLED
            );
        };
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
