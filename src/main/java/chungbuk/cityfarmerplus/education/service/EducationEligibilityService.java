package chungbuk.cityfarmerplus.education.service;

import chungbuk.cityfarmerplus.common.exception.DomainException;
import chungbuk.cityfarmerplus.education.repository.EducationCertificateSubmissionRepository;
import chungbuk.cityfarmerplus.education.repository.EducationCertificationRepository;
import chungbuk.cityfarmerplus.education.repository.EducationCourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EducationEligibilityService {

    private final EducationCourseRepository courseRepository;
    private final EducationCertificateSubmissionRepository submissionRepository;
    private final EducationCertificationRepository certificationRepository;
    private final EducationCertificationProgressCalculator calculator;

    @Transactional(readOnly = true)
    public void requireApproved(Long userId) {
        boolean eligible = certificationRepository.findByUrbanFarmerId(userId)
                .map(certification -> calculator.calculate(
                        courseRepository
                                .findAllByActiveTrueOrderByDisplayOrderAscTitleAsc(),
                        submissionRepository
                                .findAllByCertificationIdOrderByAttemptNumberDesc(
                                        certification.getId()
                                )
                ).eligible())
                .orElse(false);
        if (!eligible) {
            throw new DomainException(
                    HttpStatus.FORBIDDEN,
                    "EDUCATION_CERTIFICATION_REQUIRED",
                    "승인된 교육 인증이 있어야 모집 공고에 지원할 수 있습니다."
            );
        }
    }
}
