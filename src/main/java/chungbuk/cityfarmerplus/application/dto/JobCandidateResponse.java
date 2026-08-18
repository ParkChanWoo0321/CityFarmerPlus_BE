package chungbuk.cityfarmerplus.application.dto;

import chungbuk.cityfarmerplus.application.entity.JobApplication;

import java.time.Instant;
import java.time.LocalDate;

public record JobCandidateResponse(
        Long applicationId,
        Long urbanFarmerUserId,
        String name,
        String phoneNumber,
        JobApplication.ApplicationStatus status,
        JobApplication.FarmOpinion farmOpinion,
        String farmOpinionNote,
        String preferredRegionsSnapshot,
        String availableDaysSnapshot,
        LocalDate preferredStartDateSnapshot,
        LocalDate preferredEndDateSnapshot,
        String availableWorkTypesSnapshot,
        Boolean canTravelSnapshot,
        int experienceCountSnapshot,
        Instant educationVerifiedAt,
        Instant appliedAt
) {

    public static JobCandidateResponse from(JobApplication application) {
        return new JobCandidateResponse(
                application.getId(),
                application.getUrbanFarmer().getId(),
                application.getUrbanFarmer().getName(),
                application.getUrbanFarmer().getPhoneNumber(),
                application.getStatus(),
                application.getFarmOpinion(),
                application.getFarmOpinionNote(),
                application.getPreferredRegionsSnapshot(),
                application.getAvailableDaysSnapshot(),
                application.getPreferredStartDateSnapshot(),
                application.getPreferredEndDateSnapshot(),
                application.getAvailableWorkTypesSnapshot(),
                application.getCanTravelSnapshot(),
                application.getExperienceCountSnapshot(),
                application.getEducationVerifiedAt(),
                application.getCreatedAt()
        );
    }
}
