package chungbuk.cityfarmerplus.farm.ownership.repository;

import chungbuk.cityfarmerplus.farm.ownership.entity.FarmOwnershipSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
