package chungbuk.cityfarmerplus.education.repository;

import chungbuk.cityfarmerplus.education.entity.EducationCertificateDocument;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EducationCertificateDocumentRepository
        extends JpaRepository<EducationCertificateDocument, Long> {

    @EntityGraph(attributePaths = {"submission.certification.urbanFarmer"})
    Optional<EducationCertificateDocument>
    findByIdAndSubmissionIdAndSubmissionCertificationUrbanFarmerId(
            Long documentId,
            Long submissionId,
            Long userId
    );

    @EntityGraph(attributePaths = {"submission.certification.urbanFarmer"})
    Optional<EducationCertificateDocument> findByIdAndSubmissionId(
            Long documentId,
            Long submissionId
    );

    List<EducationCertificateDocument>
    findAllBySubmissionCertificationUrbanFarmerId(Long userId);
}
