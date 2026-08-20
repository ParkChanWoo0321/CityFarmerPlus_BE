package chungbuk.cityfarmerplus.education.dto;

import chungbuk.cityfarmerplus.education.entity.EducationCertificateSubmission;
import chungbuk.cityfarmerplus.education.entity.EducationCourse;

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
        Instant submittedAt
) {

    public static EducationCourseProgressResponse from(
            EducationCourse course,
            EducationCertificateSubmission latestSubmission
    ) {
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
                latestSubmission == null ? null : latestSubmission.getSubmittedAt()
        );
    }
}
