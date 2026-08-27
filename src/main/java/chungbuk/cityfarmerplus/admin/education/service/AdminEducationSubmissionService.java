package chungbuk.cityfarmerplus.admin.education.service;

import chungbuk.cityfarmerplus.admin.education.dto.EducationApproveRequest;
import chungbuk.cityfarmerplus.admin.education.dto.EducationRejectRequest;
import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.exception.AuthException;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import chungbuk.cityfarmerplus.common.exception.DomainException;
import chungbuk.cityfarmerplus.education.dto.EducationSubmissionResponse;
import chungbuk.cityfarmerplus.education.entity.EducationCertificateSubmission;
import chungbuk.cityfarmerplus.education.repository.EducationCertificateSubmissionRepository;
import chungbuk.cityfarmerplus.education.service.EducationCertificationProgressSynchronizer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AdminEducationSubmissionService {

    private final EducationCertificateSubmissionRepository submissionRepository;
    private final UserRepository userRepository;
    private final EducationCertificationProgressSynchronizer progressSynchronizer;

    @Transactional(readOnly = true)
    public Page<EducationSubmissionResponse> list(Pageable pageable) {
        return submissionRepository
                .findAllByStatus(
                        EducationCertificateSubmission.SubmissionStatus.PENDING_REVIEW,
                        pageable
                )
                .map(EducationSubmissionResponse::from);
    }

    @Transactional(readOnly = true)
    public EducationSubmissionResponse getDetail(Long submissionId) {
        return submissionRepository.findDetailedById(submissionId)
                .map(EducationSubmissionResponse::from)
                .orElseThrow(this::notFound);
    }

    @Transactional
    public EducationSubmissionResponse approve(
            Long adminId,
            Long submissionId,
            EducationApproveRequest request
    ) {
        User reviewer = requireCenterAdmin(adminId);
        EducationCertificateSubmission submission = getForUpdate(submissionId);
        try {
            submission.approve(reviewer, request.recognizedHours(), Instant.now());
        } catch (IllegalStateException exception) {
            throw invalidStatus(exception.getMessage());
        } catch (IllegalArgumentException exception) {
            throw invalidRecognizedHours(exception.getMessage());
        }
        progressSynchronizer.synchronizeLocked(submission.getCertification().getId());
        return EducationSubmissionResponse.from(submission);
    }

    @Transactional
    public EducationSubmissionResponse reject(
            Long adminId,
            Long submissionId,
            EducationRejectRequest request
    ) {
        User reviewer = requireCenterAdmin(adminId);
        EducationCertificateSubmission submission = getForUpdate(submissionId);
        try {
            submission.reject(reviewer, request.reason(), Instant.now());
        } catch (IllegalStateException exception) {
            throw invalidStatus(exception.getMessage());
        }
        progressSynchronizer.synchronizeLocked(submission.getCertification().getId());
        return EducationSubmissionResponse.from(submission);
    }

    private EducationCertificateSubmission getForUpdate(Long submissionId) {
        return submissionRepository.findByIdForUpdate(submissionId)
                .orElseThrow(this::notFound);
    }

    private User requireCenterAdmin(Long adminId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(AuthException::userNotFound);
        if (!admin.isActive()) {
            throw AuthException.inactiveAccount();
        }
        if (admin.getUserType() != User.UserType.CENTER_ADMIN) {
            throw new DomainException(
                    HttpStatus.FORBIDDEN,
                    "CENTER_ADMIN_ROLE_REQUIRED",
                    "관리자 계정만 사용할 수 있습니다."
            );
        }
        return admin;
    }

    private DomainException notFound() {
        return new DomainException(
                HttpStatus.NOT_FOUND,
                "EDUCATION_SUBMISSION_NOT_FOUND",
                "교육 이수증 제출 내역을 찾을 수 없습니다."
        );
    }

    private DomainException invalidStatus(String message) {
        return new DomainException(HttpStatus.CONFLICT, "INVALID_EDUCATION_SUBMISSION_STATUS", message);
    }

    private DomainException invalidRecognizedHours(String message) {
        return new DomainException(HttpStatus.BAD_REQUEST, "INVALID_RECOGNIZED_HOURS", message);
    }
}
