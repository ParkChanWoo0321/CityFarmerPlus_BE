package chungbuk.cityfarmerplus.jobposting.dto;

import chungbuk.cityfarmerplus.application.entity.JobApplication;

public record PublicJobApplicationSummary(
        Long applicationId,
        JobApplication.ApplicationStatus status
) {

    public static PublicJobApplicationSummary from(JobApplication application) {
        return new PublicJobApplicationSummary(
                application.getId(),
                application.getStatus()
        );
    }
}
