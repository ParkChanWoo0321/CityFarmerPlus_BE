package chungbuk.cityfarmerplus.education.dto;

import chungbuk.cityfarmerplus.education.entity.EducationCertificateDocument;

import java.time.Instant;

public record EducationDocumentResponse(
        Long id,
        int displayOrder,
        String originalFilename,
        String contentType,
        long sizeBytes,
        String sha256,
        Instant createdAt
) {
    public static EducationDocumentResponse from(EducationCertificateDocument document) {
        return new EducationDocumentResponse(
                document.getId(),
                document.getDisplayOrder(),
                document.getOriginalFilename(),
                document.getContentType(),
                document.getSizeBytes(),
                document.getSha256(),
                document.getCreatedAt()
        );
    }
}
