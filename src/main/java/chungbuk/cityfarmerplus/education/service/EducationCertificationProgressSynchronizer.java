package chungbuk.cityfarmerplus.education.service;

import chungbuk.cityfarmerplus.common.exception.DomainException;
import chungbuk.cityfarmerplus.education.entity.EducationCertification;
import chungbuk.cityfarmerplus.education.repository.EducationCertificateSubmissionRepository;
import chungbuk.cityfarmerplus.education.repository.EducationCertificationRepository;
import chungbuk.cityfarmerplus.education.repository.EducationCourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EducationCertificationProgressSynchronizer {

    private final EducationCertificationRepository certificationRepository;
    private final EducationCourseRepository courseRepository;
    private final EducationCertificateSubmissionRepository submissionRepository;
    private final EducationCertificationProgressCalculator calculator;

    @Transactional
    public void synchronizeLocked(Long certificationId) {
        EducationCertification certification = certificationRepository
                .findByIdForUpdate(certificationId)
                .orElseThrow(() -> new DomainException(
                        HttpStatus.NOT_FOUND,
                        "EDUCATION_CERTIFICATION_NOT_FOUND",
                        "교육 인증 정보를 찾을 수 없습니다."
                ));
        var result = calculator.calculate(
                courseRepository.findAllByActiveTrueOrderByDisplayOrderAscTitleAsc(),
                submissionRepository
                        .findAllByCertificationIdOrderByAttemptNumberDesc(
                                certification.getId()
                        )
        );
        certification.synchronizeProgress(
                result.status(),
                result.representativeApprovedSubmission(),
                result.recognizedHours(),
                result.approvedAt()
        );
    }
}
