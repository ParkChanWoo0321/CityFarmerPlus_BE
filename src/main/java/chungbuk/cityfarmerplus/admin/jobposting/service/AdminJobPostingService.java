package chungbuk.cityfarmerplus.admin.jobposting.service;

import chungbuk.cityfarmerplus.admin.jobposting.dto.AdminJobPostingUpdateRequest;
import chungbuk.cityfarmerplus.admin.jobposting.dto.JobPostingRejectRequest;
import chungbuk.cityfarmerplus.application.entity.JobApplication;
import chungbuk.cityfarmerplus.application.repository.JobApplicationRepository;
import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.exception.AuthException;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import chungbuk.cityfarmerplus.common.exception.DomainException;
import chungbuk.cityfarmerplus.jobposting.dto.JobPostingResponse;
import chungbuk.cityfarmerplus.jobposting.dto.JobPostingReviewResponse;
import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;
import chungbuk.cityfarmerplus.jobposting.entity.JobPostingReview;
import chungbuk.cityfarmerplus.jobposting.exception.JobPostingException;
import chungbuk.cityfarmerplus.jobposting.repository.JobPostingRepository;
import chungbuk.cityfarmerplus.jobposting.repository.JobPostingReviewRepository;
import chungbuk.cityfarmerplus.jobposting.service.JobPostingResponseAssembler;
import chungbuk.cityfarmerplus.jobposting.service.JobPostingScheduleValidator;
import chungbuk.cityfarmerplus.jobposting.service.JobPostingSpecifications;
import chungbuk.cityfarmerplus.work.entity.WorkAssignment;
import chungbuk.cityfarmerplus.work.repository.WorkAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminJobPostingService {

    private final JobPostingRepository jobPostingRepository;
    private final JobPostingReviewRepository jobPostingReviewRepository;
    private final JobPostingResponseAssembler responseAssembler;
    private final JobPostingScheduleValidator scheduleValidator;
    private final JobApplicationRepository jobApplicationRepository;
    private final WorkAssignmentRepository workAssignmentRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<JobPostingResponse> list(Pageable pageable) {
        Page<JobPosting> postings = jobPostingRepository.findAll(
                JobPostingSpecifications.hasStatus(JobPosting.JobPostingStatus.PENDING_REVIEW),
                pageable
        );
        return new PageImpl<>(
                responseAssembler.assembleAll(postings.getContent()),
                pageable,
                postings.getTotalElements()
        );
    }

    @Transactional
    public JobPostingReviewResponse approve(Long adminId, Long postingId) {
        User reviewer = requireCenterAdmin(adminId);
        JobPosting posting = getForUpdate(postingId);
        try {
            posting.approve(Instant.now());
        } catch (IllegalStateException exception) {
            throw JobPostingException.invalidState(exception.getMessage());
        }
        JobPostingReview review = jobPostingReviewRepository.save(
                JobPostingReview.record(
                        posting,
                        reviewer,
                        JobPostingReview.ReviewAction.APPROVED,
                        null
                )
        );
        return JobPostingReviewResponse.from(review);
    }

    @Transactional
    public JobPostingReviewResponse reject(
            Long adminId,
            Long postingId,
            JobPostingRejectRequest request
    ) {
        User reviewer = requireCenterAdmin(adminId);
        JobPosting posting = getForUpdate(postingId);
        try {
            posting.reject();
        } catch (IllegalStateException exception) {
            throw JobPostingException.invalidState(exception.getMessage());
        }
        JobPostingReview review = jobPostingReviewRepository.save(
                JobPostingReview.record(
                        posting,
                        reviewer,
                        JobPostingReview.ReviewAction.REJECTED,
                        request.reason()
                )
        );
        return JobPostingReviewResponse.from(review);
    }

    @Transactional
    public JobPostingResponse update(
            Long adminId,
            Long postingId,
            AdminJobPostingUpdateRequest request
    ) {
        User reviewer = requireCenterAdmin(adminId);
        scheduleValidator.validate(
                request.workDate(),
                request.startTime(),
                request.endTime()
        );
        JobPosting posting = getForUpdate(postingId);
        long matchedCount = jobApplicationRepository.countByJobPostingIdAndStatus(
                postingId,
                JobApplication.ApplicationStatus.MATCHED
        );
        if (matchedCount > 0) {
            throw JobPostingException.matchedPostingUpdateNotAllowed();
        }
        if (request.capacity() < matchedCount) {
            throw JobPostingException.capacityBelowMatchedCount();
        }
        try {
            posting.updateByAdmin(request.toDetails());
        } catch (IllegalStateException exception) {
            throw JobPostingException.invalidState(exception.getMessage());
        } catch (IllegalArgumentException exception) {
            throw JobPostingException.invalidDetails(exception.getMessage());
        }
        JobPostingReview review = jobPostingReviewRepository.save(
                JobPostingReview.record(
                        posting,
                        reviewer,
                        JobPostingReview.ReviewAction.EDITED,
                        request.reason()
                )
        );
        return JobPostingResponse.from(posting, review);
    }

    @Transactional
    public JobPostingReviewResponse close(Long adminId, Long postingId) {
        User reviewer = requireCenterAdmin(adminId);
        JobPosting posting = getForUpdate(postingId);
        try {
            posting.close(Instant.now());
        } catch (IllegalStateException exception) {
            throw JobPostingException.invalidState(exception.getMessage());
        }
        jobApplicationRepository.findByJobPostingIdAndStatus(
                        postingId,
                        JobApplication.ApplicationStatus.APPLIED
                )
                .forEach(JobApplication::markNotMatched);
        JobPostingReview review = jobPostingReviewRepository.save(
                JobPostingReview.record(
                        posting,
                        reviewer,
                        JobPostingReview.ReviewAction.CLOSED,
                        null
                )
        );
        return JobPostingReviewResponse.from(review);
    }

    @Transactional
    public JobPostingReviewResponse cancel(Long adminId, Long postingId) {
        User reviewer = requireCenterAdmin(adminId);
        JobPosting posting = getForUpdate(postingId);
        try {
            posting.cancel(Instant.now());
        } catch (IllegalStateException exception) {
            throw JobPostingException.invalidState(exception.getMessage());
        }
        jobApplicationRepository.findByJobPostingIdOrderByCreatedAtAsc(postingId)
                .forEach(JobApplication::cancelWithPostingByAdmin);
        workAssignmentRepository.findByJobPostingIdOrderByCreatedAtAsc(postingId).stream()
                .filter(assignment -> assignment.getStatus() == WorkAssignment.WorkStatus.SCHEDULED)
                .forEach(WorkAssignment::cancel);
        JobPostingReview review = jobPostingReviewRepository.save(
                JobPostingReview.record(
                        posting,
                        reviewer,
                        JobPostingReview.ReviewAction.CANCELLED,
                        null
                )
        );
        return JobPostingReviewResponse.from(review);
    }

    @Transactional(readOnly = true)
    public List<JobPostingReviewResponse> getReviewHistory(Long postingId) {
        if (!jobPostingRepository.existsById(postingId)) {
            throw JobPostingException.notFound();
        }
        return jobPostingReviewRepository.findByJobPostingIdOrderByCreatedAtDescIdDesc(postingId)
                .stream()
                .map(JobPostingReviewResponse::from)
                .toList();
    }

    private JobPosting getForUpdate(Long postingId) {
        return jobPostingRepository.findByIdForUpdate(postingId)
                .orElseThrow(JobPostingException::notFound);
    }

    private User requireCenterAdmin(Long adminId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(AuthException::userNotFound);
        if (!admin.isActive()) {
            throw AuthException.inactiveAccount();
        }
        if (admin.getUserType() != User.UserType.CENTER_ADMIN) {
            throw new DomainException(
                    HttpStatus.FORBIDDEN,
                    "CENTER_ADMIN_ROLE_REQUIRED",
                    "관리자 계정만 사용할 수 있습니다."
            );
        }
        return admin;
    }
}
