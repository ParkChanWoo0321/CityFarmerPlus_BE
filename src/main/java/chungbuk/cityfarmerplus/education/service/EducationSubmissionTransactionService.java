package chungbuk.cityfarmerplus.education.service;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.common.exception.DomainException;
import chungbuk.cityfarmerplus.education.dto.EducationSubmissionRequest;
import chungbuk.cityfarmerplus.education.dto.EducationSubmissionResponse;
import chungbuk.cityfarmerplus.education.entity.EducationCertificateSubmission;
import chungbuk.cityfarmerplus.education.entity.EducationCertification;
import chungbuk.cityfarmerplus.education.entity.EducationCourse;
import chungbuk.cityfarmerplus.education.repository.EducationCertificateSubmissionRepository;
import chungbuk.cityfarmerplus.education.repository.EducationCertificationRepository;
import chungbuk.cityfarmerplus.education.repository.EducationCourseRepository;
import chungbuk.cityfarmerplus.urbanfarmer.service.UserRoleAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EducationSubmissionTransactionService {

    private final EducationCertificationRepository certificationRepository;
    private final EducationCertificateSubmissionRepository submissionRepository;
    private final EducationCourseRepository courseRepository;
    private final UserRoleAccessService accessService;
    private final EducationSubmissionPolicy submissionPolicy;
    private final EducationCertificationProgressSynchronizer progressSynchronizer;

    @Transactional
    public EducationSubmissionResponse persist(
            Long userId,
            EducationSubmissionRequest request,
            List<StoredEducationDocument> storedDocuments
    ) {
        User urbanFarmer = accessService.requireUrbanFarmerForUpdate(userId);
        EducationCourse course = courseRepository.findById(request.courseId())
                .filter(EducationCourse::isActive)
                .orElseThrow(() -> error(
                        HttpStatus.NOT_FOUND,
                        "ACTIVE_EDUCATION_COURSE_NOT_FOUND",
                        "제출 가능한 교육 과정을 찾을 수 없습니다."
                ));
        validateHours(request.completionHours(), course);

        EducationCertification certification = certificationRepository
                .findByUrbanFarmerIdForUpdate(userId)
                .orElseGet(() -> certificationRepository.saveAndFlush(
                        EducationCertification.create(urbanFarmer)
                ));
        var latestCourseSubmission = submissionRepository
                .findTopByCertificationIdAndCourseIdOrderByAttemptNumberDesc(
                        certification.getId(),
                        course.getId()
                );
        submissionPolicy.requireSubmittable(
                latestCourseSubmission.orElse(null),
                course
        );

        int attemptNumber = submissionRepository
                .findMaxAttemptNumberByCertificationId(certification.getId()) + 1;
        EducationCertificateSubmission submission =
                EducationCertificateSubmission.createPending(
                        certification,
                        course,
                        attemptNumber,
                        request.completionDate(),
                        request.completionHours()
                );
        storedDocuments.forEach(document -> submission.addDocument(
                document.originalFilename(),
                document.storageKey(),
                document.contentType(),
                document.sizeBytes(),
                document.sha256()
        ));
        try {
            EducationCertificateSubmission saved =
                    submissionRepository.saveAndFlush(submission);
            progressSynchronizer.synchronizeLocked(certification.getId());
            return EducationSubmissionResponse.from(saved);
        } catch (DataIntegrityViolationException exception) {
            throw error(
                    HttpStatus.CONFLICT,
                    "EDUCATION_SUBMISSION_DATA_CONFLICT",
                    "동시에 처리된 이수증 제출과 충돌했습니다. 다시 시도해 주세요."
            );
        }
    }

    private void validateHours(int completionHours, EducationCourse course) {
        int minimumHours = Math.max(8, course.getRequiredHours());
        if (completionHours < minimumHours) {
            throw error(
                    HttpStatus.BAD_REQUEST,
                    "INSUFFICIENT_EDUCATION_HOURS",
                    "해당 교육은 " + minimumHours + "시간 이상 이수해야 제출할 수 있습니다."
            );
        }
    }

    private DomainException error(HttpStatus status, String code, String message) {
        return new DomainException(status, code, message);
    }
}
