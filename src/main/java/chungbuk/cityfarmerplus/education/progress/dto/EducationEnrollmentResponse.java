package chungbuk.cityfarmerplus.education.progress.dto;

import chungbuk.cityfarmerplus.education.progress.entity.EducationEnrollment;

import java.time.Instant;

public record EducationEnrollmentResponse(
        Long enrollmentId,
        Long urbanFarmerId,
        Long courseId,
        String provider,
        String externalEnrollmentId,
        EducationEnrollment.ProgressStatus progressStatus,
        int totalMinutes,
        int completedMinutes,
        int remainingMinutes,
        int progressPercentage,
        Instant startedAt,
        Instant completedAt,
        Instant providerUpdatedAt,
        Instant lastSyncedAt,
        long version
) {
    public static EducationEnrollmentResponse from(EducationEnrollment enrollment) {
        return new EducationEnrollmentResponse(
                enrollment.getId(),
                enrollment.getUrbanFarmer().getId(),
                enrollment.getCourse().getId(),
                enrollment.getProvider(),
                enrollment.getExternalEnrollmentId(),
                enrollment.getProgressStatus(),
                enrollment.getTotalMinutes(),
                enrollment.getCompletedMinutes(),
                enrollment.remainingMinutes(),
                enrollment.progressPercentage(),
                enrollment.getStartedAt(),
                enrollment.getCompletedAt(),
                enrollment.getProviderUpdatedAt(),
                enrollment.getLastSyncedAt(),
                enrollment.getVersion()
        );
    }
}
