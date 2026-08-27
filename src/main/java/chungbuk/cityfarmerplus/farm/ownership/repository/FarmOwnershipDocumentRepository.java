package chungbuk.cityfarmerplus.farm.ownership.repository;

import chungbuk.cityfarmerplus.farm.ownership.entity.FarmOwnershipDocument;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FarmOwnershipDocumentRepository
        extends JpaRepository<FarmOwnershipDocument, Long> {

    @Override
    @EntityGraph(attributePaths = {
            "submission",
            "submission.farmProfile",
            "submission.farmProfile.owner"
    })
    Optional<FarmOwnershipDocument> findById(Long id);

    @EntityGraph(attributePaths = {
            "submission",
            "submission.farmProfile",
            "submission.farmProfile.owner"
    })
    Optional<FarmOwnershipDocument> findByIdAndSubmissionFarmProfileId(
            Long documentId,
            Long profileId
    );

    List<FarmOwnershipDocument> findAllBySubmissionFarmProfileOwnerId(Long ownerUserId);
}
