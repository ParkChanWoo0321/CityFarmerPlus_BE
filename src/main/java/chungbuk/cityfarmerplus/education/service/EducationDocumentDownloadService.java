package chungbuk.cityfarmerplus.education.service;

import chungbuk.cityfarmerplus.common.exception.DomainException;
import chungbuk.cityfarmerplus.common.storage.FileStorage;
import chungbuk.cityfarmerplus.common.storage.FileStorageException;
import chungbuk.cityfarmerplus.education.entity.EducationCertificateDocument;
import chungbuk.cityfarmerplus.education.repository.EducationCertificateDocumentRepository;
import chungbuk.cityfarmerplus.urbanfarmer.service.UserRoleAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EducationDocumentDownloadService {

    private final EducationCertificateDocumentRepository documentRepository;
    private final UserRoleAccessService accessService;
    private final FileStorage fileStorage;

    @Transactional(readOnly = true)
    public DownloadedEducationDocument downloadMine(
            Long userId,
            Long submissionId,
            Long documentId
    ) {
        accessService.requireUrbanFarmer(userId);
        EducationCertificateDocument document = documentRepository
                .findByIdAndSubmissionIdAndSubmissionCertificationUrbanFarmerId(
                        documentId,
                        submissionId,
                        userId
                )
                .orElseThrow(this::notFound);
        return load(document, true);
    }

    @Transactional(readOnly = true)
    public DownloadedEducationDocument downloadForAdmin(
            Long submissionId,
            Long documentId
    ) {
        EducationCertificateDocument document = documentRepository
                .findByIdAndSubmissionId(documentId, submissionId)
                .orElseThrow(this::notFound);
        return load(document, false);
    }

    private DownloadedEducationDocument load(
            EducationCertificateDocument document,
            boolean activeOwnerRequired
    ) {
        if (activeOwnerRequired && !document.getSubmission()
                .getCertification()
                .getUrbanFarmer()
                .isActive()) {
            throw unavailable();
        }
        try {
            return new DownloadedEducationDocument(
                    fileStorage.load(document.getStorageKey()),
                    document.getOriginalFilename(),
                    document.getContentType(),
                    document.getSizeBytes()
            );
        } catch (FileStorageException exception) {
            throw unavailable();
        }
    }

    private DomainException unavailable() {
        return new DomainException(
                HttpStatus.GONE,
                "EDUCATION_DOCUMENT_FILE_UNAVAILABLE",
                "보관이 종료되었거나 사용할 수 없는 교육 이수증 파일입니다."
        );
    }

    private DomainException notFound() {
        return new DomainException(
                HttpStatus.NOT_FOUND,
                "EDUCATION_DOCUMENT_NOT_FOUND",
                "교육 이수증 파일을 찾을 수 없습니다."
        );
    }

    public record DownloadedEducationDocument(
            Resource resource,
            String originalFilename,
            String contentType,
            long sizeBytes
    ) {
    }
}
