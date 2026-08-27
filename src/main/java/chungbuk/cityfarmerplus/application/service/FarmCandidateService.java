package chungbuk.cityfarmerplus.application.service;

import chungbuk.cityfarmerplus.application.dto.FarmOpinionRequest;
import chungbuk.cityfarmerplus.application.dto.JobCandidateResponse;
import chungbuk.cityfarmerplus.application.entity.JobApplication;
import chungbuk.cityfarmerplus.application.exception.JobApplicationException;
import chungbuk.cityfarmerplus.application.repository.JobApplicationRepository;
import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;
import chungbuk.cityfarmerplus.jobposting.exception.JobPostingException;
import chungbuk.cityfarmerplus.jobposting.repository.JobPostingRepository;
import chungbuk.cityfarmerplus.jobposting.service.JobPostingAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FarmCandidateService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final JobPostingAccessService accessService;
    private final JobPostingRepository postingRepository;
    private final JobApplicationRepository applicationRepository;

    public List<JobCandidateResponse> getCandidates(Long farmUserId, Long postingId) {
        accessService.requireApprovedFarm(farmUserId);
        getOwnedPosting(farmUserId, postingId);
        return applicationRepository.findByJobPostingIdOrderByCreatedAtAsc(postingId)
                .stream()
                .map(JobCandidateResponse::from)
                .toList();
    }

    @Transactional
    public JobCandidateResponse updateOpinion(
            Long farmUserId,
            Long postingId,
            Long applicationId,
            FarmOpinionRequest request
    ) {
        accessService.requireApprovedFarmForUpdate(farmUserId);
        JobPosting posting = getOwnedPostingForUpdate(farmUserId, postingId);
        if (!posting.isAcceptingApplications(
                LocalDate.now(SERVICE_ZONE),
                LocalTime.now(SERVICE_ZONE)
        )) {
            throw JobPostingException.invalidState(
                    "작업 시작 전 모집 중인 공고의 지원자 의견만 수정할 수 있습니다."
            );
        }
        JobApplication application = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(JobApplicationException::notFound);
        if (!application.getJobPosting().getId().equals(postingId)) {
            throw JobApplicationException.notFound();
        }
        try {
            application.updateFarmOpinion(
                    request.opinion(),
                    request.note() == null || request.note().isBlank()
                            ? null
                            : request.note().trim()
            );
        } catch (IllegalStateException exception) {
            throw JobApplicationException.invalidState(exception.getMessage());
        }
        return JobCandidateResponse.from(application);
    }

    private JobPosting getOwnedPosting(Long farmUserId, Long postingId) {
        JobPosting posting = postingRepository.findById(postingId)
                .orElseThrow(JobPostingException::notFound);
        if (!posting.getFarmProfile().getOwner().getId().equals(farmUserId)) {
            throw JobPostingException.notOwner();
        }
        return posting;
    }

    private JobPosting getOwnedPostingForUpdate(Long farmUserId, Long postingId) {
        JobPosting posting = postingRepository.findByIdForUpdate(postingId)
                .orElseThrow(JobPostingException::notFound);
        if (!posting.getFarmProfile().getOwner().getId().equals(farmUserId)) {
            throw JobPostingException.notOwner();
        }
        return posting;
    }
}
