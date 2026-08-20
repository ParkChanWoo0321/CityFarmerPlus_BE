package chungbuk.cityfarmerplus.education.service;

import chungbuk.cityfarmerplus.common.exception.DomainException;
import chungbuk.cityfarmerplus.common.storage.FileStorage;
import chungbuk.cityfarmerplus.common.storage.FileDeletionScheduler;
import chungbuk.cityfarmerplus.education.dto.EducationCertificationResponse;
import chungbuk.cityfarmerplus.education.dto.EducationSubmissionRequest;
import chungbuk.cityfarmerplus.education.dto.EducationSubmissionResponse;
import chungbuk.cityfarmerplus.education.entity.EducationCourse;
import chungbuk.cityfarmerplus.education.repository.EducationCertificateSubmissionRepository;
import chungbuk.cityfarmerplus.education.repository.EducationCertificationRepository;
import chungbuk.cityfarmerplus.education.repository.EducationCourseRepository;
import chungbuk.cityfarmerplus.urbanfarmer.service.UserRoleAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EducationSubmissionService {

    private final EducationCertificationRepository certificationRepository;
    private final EducationCertificateSubmissionRepository submissionRepository;
    private final EducationCourseRepository courseRepository;
    private final UserRoleAccessService accessService;
    private final EducationDocumentValidator documentValidator;
    private final FileStorage fileStorage;
    private final FileDeletionScheduler fileDeletionScheduler;
    private final EducationSubmissionPolicy submissionPolicy;
    private final EducationSubmissionTransactionService transactionService;
    private final EducationProgressService progressService;

    public EducationSubmissionResponse submit(
            Long userId,
            EducationSubmissionRequest request,
            List<MultipartFile> documents
    ) {
        accessService.requireUrbanFarmer(userId);
        EducationCourse course = courseRepository.findById(request.courseId())
                .filter(EducationCourse::isActive)
                .orElseThrow(() -> error(
                        HttpStatus.NOT_FOUND,
                        "ACTIVE_EDUCATION_COURSE_NOT_FOUND",
                        "제출 가능한 교육 과정을 찾을 수 없습니다."
                ));
        if (request.completionHours() < Math.max(8, course.getRequiredHours())) {
            throw error(
                    HttpStatus.BAD_REQUEST,
                    "INSUFFICIENT_EDUCATION_HOURS",
                    "필요한 교육 이수 시간을 충족하지 못했습니다."
            );
        }
        certificationRepository.findByUrbanFarmerId(userId)
                .flatMap(certification -> submissionRepository
                        .findTopByCertificationIdAndCourseIdOrderByAttemptNumberDesc(
                                certification.getId(),
                                course.getId()
                        ))
                .ifPresent(latest -> submissionPolicy.requireSubmittable(latest, course));
        List<EducationDocumentValidator.ValidatedEducationDocument> validated =
                documentValidator.validate(documents);
        List<StoredEducationDocument> stored = storeDocuments(userId, validated);
        try {
            return transactionService.persist(userId, request, stored);
        } catch (RuntimeException exception) {
            deleteWithRetry(stored);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public EducationCertificationResponse getCurrent(Long userId) {
        accessService.requireUrbanFarmer(userId);
        return progressService.getProgress(
                userId,
                certificationRepository.findByUrbanFarmerId(userId).orElse(null)
        );
    }

    @Transactional(readOnly = true)
    public List<EducationSubmissionResponse> getHistory(Long userId) {
        accessService.requireUrbanFarmer(userId);
        return submissionRepository
                .findAllByCertificationUrbanFarmerIdOrderByAttemptNumberDesc(userId)
                .stream()
                .map(EducationSubmissionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public EducationSubmissionResponse getMine(Long userId, Long submissionId) {
        accessService.requireUrbanFarmer(userId);
        return submissionRepository
                .findByIdAndCertificationUrbanFarmerId(submissionId, userId)
                .map(EducationSubmissionResponse::from)
                .orElseThrow(this::submissionNotFound);
    }

    private List<StoredEducationDocument> storeDocuments(
            Long userId,
            List<EducationDocumentValidator.ValidatedEducationDocument> documents
    ) {
        String directory = "education-certification/" + userId + "/" + UUID.randomUUID();
        List<StoredEducationDocument> stored = new ArrayList<>();
        try {
            for (EducationDocumentValidator.ValidatedEducationDocument document : documents) {
                FileStorage.StoredFile result = fileStorage.store(
                        directory,
                        document.source(),
                        document.storageExtension(),
                        EducationDocumentValidator.MAX_DOCUMENT_SIZE
                );
                StoredEducationDocument storedDocument =
                        new StoredEducationDocument(
                                document.originalFilename(),
                                result.storageKey(),
                                document.contentType(),
                                result.sizeBytes(),
                                result.sha256()
                        );
                stored.add(storedDocument);
                if (result.sizeBytes() != document.size()
                        || !result.sha256().equals(document.sha256())) {
                    throw new IllegalStateException("검증한 파일과 저장된 파일이 일치하지 않습니다.");
                }
            }
            return List.copyOf(stored);
        } catch (RuntimeException exception) {
            deleteWithRetry(stored);
            throw error(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "EDUCATION_DOCUMENT_STORAGE_FAILED",
                    "이수증 파일을 저장하지 못했습니다."
            );
        }
    }

    private void deleteWithRetry(List<StoredEducationDocument> documents) {
        fileDeletionScheduler.deleteNowWithRetry(
                documents.stream()
                        .map(StoredEducationDocument::storageKey)
                        .toList(),
                "EDUCATION_UPLOAD_COMPENSATION"
        );
    }

    private DomainException submissionNotFound() {
        return error(
                HttpStatus.NOT_FOUND,
                "EDUCATION_SUBMISSION_NOT_FOUND",
                "교육 이수증 제출 내역을 찾을 수 없습니다."
        );
    }

    private DomainException error(HttpStatus status, String code, String message) {
        return new DomainException(status, code, message);
    }
}
