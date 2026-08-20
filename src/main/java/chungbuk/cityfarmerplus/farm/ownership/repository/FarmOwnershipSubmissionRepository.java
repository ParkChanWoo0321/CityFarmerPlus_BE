package chungbuk.cityfarmerplus.farm.ownership.repository;

import chungbuk.cityfarmerplus.farm.ownership.entity.FarmOwnershipSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface FarmOwnershipSubmissionRepository
        extends JpaRepository<FarmOwnershipSubmission, Long> {

    @Query("""
            select coalesce(max(submission.attemptNumber), 0)
            from FarmOwnershipSubmission submission
            where submission.farmProfile.id = :farmProfileId
            """)
    int findMaxAttemptNumberByFarmProfileId(
            @Param("farmProfileId") Long farmProfileId
    );

    @Query("""
            select distinct submission
            from FarmOwnershipSubmission submission
            left join fetch submission.documents
            left join fetch submission.reviewer
            where submission.farmProfile.owner.id = :ownerId
            order by submission.attemptNumber desc
            """)
    List<FarmOwnershipSubmission> findAllDetailedByOwnerId(
            @Param("ownerId") Long ownerId
    );

    @Query("""
            select distinct submission
            from FarmOwnershipSubmission submission
            left join fetch submission.documents
            left join fetch submission.reviewer
            where submission.id = :submissionId
              and submission.farmProfile.owner.id = :ownerId
            """)
    Optional<FarmOwnershipSubmission> findDetailedByIdAndOwnerId(
            @Param("submissionId") Long submissionId,
            @Param("ownerId") Long ownerId
    );

    @Query("""
            select distinct submission
            from FarmOwnershipSubmission submission
            left join fetch submission.documents
            left join fetch submission.reviewer
            where submission.farmProfile.id = :farmProfileId
            order by submission.attemptNumber desc
            """)
    List<FarmOwnershipSubmission> findAllDetailedByFarmProfileId(
            @Param("farmProfileId") Long farmProfileId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select submission
            from FarmOwnershipSubmission submission
            where submission.farmProfile.id = :farmProfileId
              and submission.attemptNumber = (
                  select max(candidate.attemptNumber)
                  from FarmOwnershipSubmission candidate
                  where candidate.farmProfile.id = :farmProfileId
              )
            """)
    Optional<FarmOwnershipSubmission> findLatestByFarmProfileIdForUpdate(
            @Param("farmProfileId") Long farmProfileId
    );
}
