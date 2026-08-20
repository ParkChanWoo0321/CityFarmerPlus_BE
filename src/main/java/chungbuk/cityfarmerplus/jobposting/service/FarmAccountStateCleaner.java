package chungbuk.cityfarmerplus.jobposting.service;

import chungbuk.cityfarmerplus.application.entity.JobApplication;
import chungbuk.cityfarmerplus.application.repository.JobApplicationRepository;
import chungbuk.cityfarmerplus.auth.service.AccountDataCleaner;
import chungbuk.cityfarmerplus.common.exception.DomainException;
import chungbuk.cityfarmerplus.farm.repository.FarmProfileRepository;
import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;
import chungbuk.cityfarmerplus.jobposting.repository.JobPostingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Order(0)
@RequiredArgsConstructor
public class FarmAccountStateCleaner implements AccountDataCleaner {

    private final FarmProfileRepository farmProfileRepository;
    private final JobPostingRepository postingRepository;
    private final JobApplicationRepository applicationRepository;

    @Override
    public void clean(Long userId) {
        var profile = farmProfileRepository.findByOwnerIdForUpdate(userId).orElse(null);
        if (profile == null) {
            return;
        }
        var postings = postingRepository.findAllByFarmOwnerIdForUpdate(userId);
        boolean hasConfirmedWork = postings.stream()
                .filter(this::isActivePosting)
                .anyMatch(posting -> applicationRepository
                        .existsByJobPostingIdAndStatus(
                                posting.getId(),
                                JobApplication.ApplicationStatus.MATCHED
                        ));
        if (hasConfirmedWork) {
            throw new DomainException(
                    HttpStatus.CONFLICT,
                    "UPCOMING_WORK_EXISTS",
                    "확정된 근무가 있으면 계정을 탈퇴할 수 없습니다. 담당자에게 문의해 주세요."
            );
        }

        for (JobPosting posting : postings) {
            if (!isActivePosting(posting)) {
                continue;
            }
            posting.cancel(Instant.now());
            applicationRepository.findByJobPostingIdAndStatus(
                    posting.getId(),
                    JobApplication.ApplicationStatus.APPLIED
            ).forEach(JobApplication::cancelWithPosting);
        }
        profile.deactivate();
    }

    private boolean isActivePosting(JobPosting posting) {
        return posting.getStatus() != JobPosting.JobPostingStatus.CANCELLED
                && posting.getStatus() != JobPosting.JobPostingStatus.WORK_COMPLETED;
    }
}
