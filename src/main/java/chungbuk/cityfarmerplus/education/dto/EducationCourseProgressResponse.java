package chungbuk.cityfarmerplus.education.dto;

import chungbuk.cityfarmerplus.education.entity.EducationCertificateSubmission;
import chungbuk.cityfarmerplus.education.entity.EducationCourse;
import chungbuk.cityfarmerplus.education.progress.entity.EducationEnrollment;

import java.time.Instant;

public record EducationCourseProgressResponse(
        Long courseId,
        String title,
        String description,
        int requiredHours,
        String externalApplicationUrl,
        boolean mandatory,
        EducationCertificateSubmission.SubmissionStatus latestSubmissionStatus,
        Long latestSubmissionId,
        Integer attemptNumber,
        Integer recognizedHours,
        String rejectionReason,
        Instant submittedAt,
        EducationEnrollment.ProgressStatus progressStatus,
        int totalMinutes,
        int completedMinutes,
        int remainingMinutes,
        int progressPercentage,
        Instant startedAt,
        Instant completedAt,
        Instant progressUpdatedAt,
        Instant lastSyncedAt
) {

    public static EducationCourseProgressResponse from(
            EducationCourse course,
            EducationCertificateSubmission latestSubmission,
            EducationEnrollment enrollment
    ) {
        int defaultTotalMinutes = Math.multiplyExact(course.getRequiredHours(), 60);
        return new EducationCourseProgressResponse(
                course.getId(),
                course.getTitle(),
                course.getDescription(),
                course.getRequiredHours(),
                course.getExternalApplicationUrl(),
                course.isMandatory(),
                latestSubmission == null ? null : latestSubmission.getStatus(),
                latestSubmission == null ? null : latestSubmission.getId(),
                latestSubmission == null ? null : latestSubmission.getAttemptNumber(),
                latestSubmission == null ? null : latestSubmission.getRecognizedHours(),
                latestSubmission == null ? null : latestSubmission.getRejectionReason(),
                latestSubmission == null ? null : latestSubmission.getSubmittedAt(),
                enrollment == null
                        ? EducationEnrollment.ProgressStatus.NOT_STARTED
                        : enrollment.getProgressStatus(),
                enrollment == null ? defaultTotalMinutes : enrollment.getTotalMinutes(),
                enrollment == null ? 0 : enrollment.getCompletedMinutes(),
                enrollment == null ? defaultTotalMinutes : enrollment.remainingMinutes(),
                enrollment == null ? 0 : enrollment.progressPercentage(),
                enrollment == null ? null : enrollment.getStartedAt(),
                enrollment == null ? null : enrollment.getCompletedAt(),
                enrollment == null ? null : enrollment.getProviderUpdatedAt(),
                enrollment == null ? null : enrollment.getLastSyncedAt()
        );
    }
}
