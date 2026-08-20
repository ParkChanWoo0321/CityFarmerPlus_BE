package chungbuk.cityfarmerplus.education.dto;

import chungbuk.cityfarmerplus.education.entity.EducationCertification;

import java.time.Instant;
import java.util.List;

public record EducationCertificationResponse(
        Long id,
        Long urbanFarmerId,
        EducationCertification.CertificationStatus status,
        Long approvedSubmissionId,
        Integer recognizedHours,
        Instant approvedAt,
        long version,
        Instant createdAt,
        Instant updatedAt,
        boolean eligibleToApply,
        long requiredCourseCount,
        long approvedRequiredCourseCount,
        List<EducationCourseProgressResponse> courses
) {
    public static EducationCertificationResponse notSubmitted(Long userId) {
        return new EducationCertificationResponse(
                null,
                userId,
                EducationCertification.CertificationStatus.NOT_SUBMITTED,
                null,
                null,
                null,
                0,
                null,
                null,
                false,
                0,
                0,
                List.of()
        );
    }

    public static EducationCertificationResponse progress(
            Long userId,
            EducationCertification certification,
            EducationCertification.CertificationStatus status,
            Long approvedSubmissionId,
            Integer recognizedHours,
            Instant approvedAt,
            boolean eligibleToApply,
            long requiredCourseCount,
            long approvedRequiredCourseCount,
            List<EducationCourseProgressResponse> courses
    ) {
        return new EducationCertificationResponse(
                certification == null ? null : certification.getId(),
                userId,
                status,
                approvedSubmissionId,
                recognizedHours,
                approvedAt,
                certification == null ? 0 : certification.getVersion(),
                certification == null ? null : certification.getCreatedAt(),
                certification == null ? null : certification.getUpdatedAt(),
                eligibleToApply,
                requiredCourseCount,
                approvedRequiredCourseCount,
                List.copyOf(courses)
        );
    }
}
