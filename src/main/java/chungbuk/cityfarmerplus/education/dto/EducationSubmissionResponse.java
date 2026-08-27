package chungbuk.cityfarmerplus.education.dto;

import chungbuk.cityfarmerplus.education.entity.EducationCertificateSubmission;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record EducationSubmissionResponse(
        Long id,
        Long certificationId,
        Long urbanFarmerId,
        String urbanFarmerName,
        Long courseId,
        String courseTitle,
        int requiredHoursSnapshot,
        int attemptNumber,
        LocalDate completionDate,
        int completionHours,
        EducationCertificateSubmission.SubmissionStatus status,
        Long reviewedByUserId,
        Instant reviewedAt,
        Integer recognizedHours,
        String rejectionReason,
        List<EducationDocumentResponse> documents,
        long version,
        Instant submittedAt
) {
    public static EducationSubmissionResponse from(
            EducationCertificateSubmission submission
    ) {
        return new EducationSubmissionResponse(
                submission.getId(),
                submission.getCertification().getId(),
                submission.getCertification().getUrbanFarmer().getId(),
                submission.getCertification().getUrbanFarmer().getName(),
                submission.getCourse().getId(),
                submission.getCourseTitleSnapshot(),
                submission.getRequiredHoursSnapshot(),
                submission.getAttemptNumber(),
                submission.getCompletionDate(),
                submission.getCompletionHours(),
                submission.getStatus(),
                submission.getReviewedBy() == null
                        ? null
                        : submission.getReviewedBy().getId(),
                submission.getReviewedAt(),
                submission.getRecognizedHours(),
                submission.getRejectionReason(),
                submission.getDocuments().stream()
                        .map(EducationDocumentResponse::from)
                        .toList(),
                submission.getVersion(),
                submission.getSubmittedAt()
        );
    }
}
