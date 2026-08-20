package chungbuk.cityfarmerplus.application.service;

import chungbuk.cityfarmerplus.application.entity.JobApplication;
import chungbuk.cityfarmerplus.application.repository.JobApplicationRepository;
import chungbuk.cityfarmerplus.auth.service.AccountDataCleaner;
import chungbuk.cityfarmerplus.common.exception.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Order(0)
@RequiredArgsConstructor
public class UrbanFarmerAccountStateCleaner implements AccountDataCleaner {

    private final JobApplicationRepository applicationRepository;

    @Override
    public void clean(Long userId) {
        var applications = applicationRepository.findAllByUrbanFarmerIdForUpdate(userId);
        if (applications.stream().anyMatch(application ->
                application.getStatus() == JobApplication.ApplicationStatus.MATCHED)) {
            throw new DomainException(
                    HttpStatus.CONFLICT,
                    "UPCOMING_WORK_EXISTS",
                    "확정된 근무가 있으면 계정을 탈퇴할 수 없습니다. 담당자에게 문의해 주세요."
            );
        }
        applications.stream()
                .filter(application -> application.getStatus()
                        == JobApplication.ApplicationStatus.APPLIED)
                .forEach(application -> application.withdraw(Instant.now()));
    }
}
