package chungbuk.cityfarmerplus.education.service;

import chungbuk.cityfarmerplus.common.exception.DomainException;
import chungbuk.cityfarmerplus.education.entity.EducationCertificateSubmission;
import chungbuk.cityfarmerplus.education.entity.EducationCourse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class EducationSubmissionPolicy {

    public void requireSubmittable(
            EducationCertificateSubmission latestSubmission,
            EducationCourse currentCourse
    ) {
        if (latestSubmission == null
                || latestSubmission.getStatus()
                == EducationCertificateSubmission.SubmissionStatus.REJECTED) {
            return;
        }
        if (latestSubmission.getStatus()
                == EducationCertificateSubmission.SubmissionStatus.APPROVED) {
            int requiredHours = Math.max(8, currentCourse.getRequiredHours());
            Integer recognizedHours = latestSubmission.getRecognizedHours();
            if (recognizedHours == null || recognizedHours < requiredHours) {
                return;
            }
        }
        throw new DomainException(
                HttpStatus.CONFLICT,
                "EDUCATION_SUBMISSION_NOT_ALLOWED",
                "해당 과정은 미제출, 반려 또는 현재 이수 기준 미달 상태에서만 다시 제출할 수 있습니다."
        );
    }
}
