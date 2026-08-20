package chungbuk.cityfarmerplus.education.service;

import chungbuk.cityfarmerplus.education.entity.EducationCertificateSubmission;
import chungbuk.cityfarmerplus.education.entity.EducationCertification;
import chungbuk.cityfarmerplus.education.entity.EducationCourse;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class EducationCertificationProgressCalculator {

    public Result calculate(
            List<EducationCourse> activeCourses,
            List<EducationCertificateSubmission> submissions
    ) {
        Map<Long, EducationCertificateSubmission> latestByCourse =
                new LinkedHashMap<>();
        submissions.forEach(submission -> latestByCourse.merge(
                submission.getCourse().getId(),
                submission,
                (existing, candidate) -> existing.getAttemptNumber()
                        >= candidate.getAttemptNumber() ? existing : candidate
        ));

        List<EducationCourse> requiredCourses = activeCourses.stream()
                .filter(EducationCourse::isMandatory)
                .toList();
        List<EducationCertificateSubmission> requiredLatest = requiredCourses.stream()
                .map(course -> latestByCourse.get(course.getId()))
                .toList();
        List<EducationCertificateSubmission> qualifyingApproved =
                requiredCourses.stream()
                        .map(course -> new CourseSubmission(
                                course,
                                latestByCourse.get(course.getId())
                        ))
                        .filter(CourseSubmission::qualifies)
                        .map(CourseSubmission::submission)
                        .toList();

        long requiredCount = requiredCourses.size();
        long approvedCount = qualifyingApproved.size();
        boolean eligible = requiredCount > 0 && approvedCount == requiredCount;
        EducationCertification.CertificationStatus status = resolveStatus(
                requiredLatest,
                eligible
        );
        int recognizedHours = qualifyingApproved.stream()
                .map(EducationCertificateSubmission::getRecognizedHours)
                .mapToInt(Integer::intValue)
                .sum();
        EducationCertificateSubmission representative = qualifyingApproved.stream()
                .max(Comparator.comparing(
                        EducationCertificateSubmission::getReviewedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())
                ))
                .orElse(null);
        Instant approvedAt = eligible && representative != null
                ? representative.getReviewedAt()
                : null;

        return new Result(
                status,
                eligible ? representative : null,
                recognizedHours == 0 ? null : recognizedHours,
                approvedAt,
                eligible,
                requiredCount,
                approvedCount,
                Map.copyOf(latestByCourse)
        );
    }

    private EducationCertification.CertificationStatus resolveStatus(
            List<EducationCertificateSubmission> requiredLatest,
            boolean eligible
    ) {
        if (eligible) {
            return EducationCertification.CertificationStatus.APPROVED;
        }
        if (hasStatus(
                requiredLatest,
                EducationCertificateSubmission.SubmissionStatus.PENDING_REVIEW
        )) {
            return EducationCertification.CertificationStatus.PENDING_REVIEW;
        }
        if (hasStatus(
                requiredLatest,
                EducationCertificateSubmission.SubmissionStatus.REJECTED
        )) {
            return EducationCertification.CertificationStatus.REJECTED;
        }
        if (hasStatus(
                requiredLatest,
                EducationCertificateSubmission.SubmissionStatus.APPROVED
        )) {
            return EducationCertification.CertificationStatus.PARTIALLY_APPROVED;
        }
        return EducationCertification.CertificationStatus.NOT_SUBMITTED;
    }

    private boolean hasStatus(
            List<EducationCertificateSubmission> submissions,
            EducationCertificateSubmission.SubmissionStatus status
    ) {
        return submissions.stream().anyMatch(submission -> submission != null
                && submission.getStatus() == status);
    }

    private record CourseSubmission(
            EducationCourse course,
            EducationCertificateSubmission submission
    ) {
        private boolean qualifies() {
            return submission != null
                    && submission.getStatus()
                    == EducationCertificateSubmission.SubmissionStatus.APPROVED
                    && submission.getRecognizedHours() != null
                    && submission.getRecognizedHours()
                    >= Math.max(8, course.getRequiredHours());
        }
    }

    public record Result(
            EducationCertification.CertificationStatus status,
            EducationCertificateSubmission representativeApprovedSubmission,
            Integer recognizedHours,
            Instant approvedAt,
            boolean eligible,
            long requiredCourseCount,
            long approvedRequiredCourseCount,
            Map<Long, EducationCertificateSubmission> latestSubmissionByCourse
    ) {
    }
}
