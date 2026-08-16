package chungbuk.cityfarmerplus.education.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "education_certificate_documents",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_education_document_storage_key",
                        columnNames = "storage_key"
                ),
                @UniqueConstraint(
                        name = "uk_education_document_order",
                        columnNames = {"submission_id", "display_order"}
                )
        },
        indexes = @Index(
                name = "idx_education_document_submission",
                columnList = "submission_id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EducationCertificateDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false, updatable = false)
    private EducationCertificateSubmission submission;

    @Column(name = "display_order", nullable = false, updatable = false)
    private int displayOrder;

    @Column(name = "original_filename", nullable = false, length = 255, updatable = false)
    private String originalFilename;

    @Column(name = "storage_key", nullable = false, length = 500, updatable = false)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 100, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 64, updatable = false)
    private String sha256;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    static EducationCertificateDocument create(
            EducationCertificateSubmission submission,
            int displayOrder,
            String originalFilename,
            String storageKey,
            String contentType,
            long sizeBytes,
            String sha256
    ) {
        EducationCertificateDocument document = new EducationCertificateDocument();
        document.submission = submission;
        document.displayOrder = displayOrder;
        document.originalFilename = originalFilename;
        document.storageKey = storageKey;
        document.contentType = contentType;
        document.sizeBytes = sizeBytes;
        document.sha256 = sha256;
        return document;
    }
}
