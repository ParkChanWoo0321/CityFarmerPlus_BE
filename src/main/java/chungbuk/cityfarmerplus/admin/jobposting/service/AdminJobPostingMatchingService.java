package chungbuk.cityfarmerplus.admin.jobposting.service;

import chungbuk.cityfarmerplus.admin.jobposting.dto.JobPostingMatchRequest;
import chungbuk.cityfarmerplus.application.dto.JobCandidateResponse;
import chungbuk.cityfarmerplus.application.entity.JobApplication;
import chungbuk.cityfarmerplus.application.exception.JobApplicationException;
import chungbuk.cityfarmerplus.application.repository.JobApplicationRepository;
import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.exception.AuthException;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import chungbuk.cityfarmerplus.common.exception.DomainException;
import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;
import chungbuk.cityfarmerplus.jobposting.exception.JobPostingException;
import chungbuk.cityfarmerplus.jobposting.repository.JobPostingRepository;
import chungbuk.cityfarmerplus.work.dto.WorkAssignmentResponse;
import chungbuk.cityfarmerplus.work.entity.WorkAssignment;
import chungbuk.cityfarmerplus.work.repository.WorkAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminJobPostingMatchingService {

    private final JobPostingRepository jobPostingRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final WorkAssignmentRepository workAssignmentRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<JobCandidateResponse> getCandidates(Long adminId, Long postingId) {
        requireCenterAdmin(adminId);
        if (!jobPostingRepository.existsById(postingId)) {
            throw JobPostingException.notFound();
        }
        return jobApplicationRepository.findByJobPostingIdOrderByCreatedAtAsc(postingId)
                .stream()
                .map(JobCandidateResponse::from)
                .toList();
    }

    @Transactional
    public List<WorkAssignmentResponse> match(
            Long adminId,
            Long postingId,
            JobPostingMatchRequest request
    ) {
        User admin = requireCenterAdmin(adminId);

        JobPosting posting = jobPostingRepository.findByIdForUpdate(postingId)
                .orElseThrow(JobPostingException::notFound);
        if (posting.getStatus() != JobPosting.JobPostingStatus.OPEN) {
            throw JobPostingException.invalidState("모집 중인 공고만 지원자를 매칭할 수 있습니다.");
        }

        List<Long> requestedIds = request.applicationIds();
        Set<Long> distinctIds = new HashSet<>(requestedIds);
        if (distinctIds.size() != requestedIds.size()) {
            throw JobApplicationException.invalidState("요청에 중복된 지원 ID가 포함되어 있습니다.");
        }

        List<JobApplication> applications =
                jobApplicationRepository.findAllByIdForUpdate(distinctIds);
        if (applications.size() != distinctIds.size()) {
            throw JobApplicationException.notFound();
        }
        for (JobApplication application : applications) {
            if (!application.getJobPosting().getId().equals(postingId)) {
                throw JobApplicationException.notFound();
            }
            if (application.getStatus() != JobApplication.ApplicationStatus.APPLIED) {
                throw JobApplicationException.invalidState("지원 완료 상태만 매칭할 수 있습니다.");
            }
        }

        Set<Long> urbanFarmerIds = applications.stream()
                .map(application -> application.getUrbanFarmer().getId())
                .collect(Collectors.toSet());
        if (userRepository.findAllByIdForUpdate(urbanFarmerIds).size()
                != urbanFarmerIds.size()) {
            throw AuthException.userNotFound();
        }

        long currentlyMatched = jobApplicationRepository
                .findByJobPostingIdAndStatusForUpdate(
                        postingId,
                        JobApplication.ApplicationStatus.MATCHED
                )
                .size();
        if (currentlyMatched + applications.size() > posting.getCapacity()) {
            throw JobApplicationException.capacityExceeded();
        }

        for (JobApplication application : applications) {
            boolean overlapping = !workAssignmentRepository
                    .findOverlappingAssignmentsForUpdate(
                            application.getUrbanFarmer().getId(),
                            posting.getWorkDate(),
                            posting.getStartTime(),
                            posting.getEndTime()
                    )
                    .isEmpty();
            if (overlapping) {
                throw JobApplicationException.overlappingAssignment();
            }
        }

        Instant now = Instant.now();
        List<WorkAssignmentResponse> results = applications.stream()
                .map(application -> {
                    try {
                        application.match(admin, now);
                    } catch (IllegalStateException exception) {
                        throw JobApplicationException.invalidState(exception.getMessage());
                    }
                    WorkAssignment assignment = workAssignmentRepository.save(
                            WorkAssignment.fromMatchedApplication(application)
                    );
                    return WorkAssignmentResponse.from(assignment);
                })
                .toList();

        long matchedCount = currentlyMatched + applications.size();
        if (matchedCount >= posting.getCapacity()) {
            posting.closeWhenCapacityReached((int) matchedCount, now);
            jobApplicationRepository
                    .findByJobPostingIdAndStatusForUpdate(
                            postingId,
                            JobApplication.ApplicationStatus.APPLIED
                    )
                    .forEach(JobApplication::markNotMatched);
        }

        return results;
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
