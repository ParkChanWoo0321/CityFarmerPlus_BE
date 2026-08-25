package chungbuk.cityfarmerplus.farm.repository;

import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FarmProfileRepository extends JpaRepository<FarmProfile, Long> {

    boolean existsByOwnerId(Long ownerId);

    Optional<FarmProfile> findByOwnerId(Long ownerId);

    @EntityGraph(attributePaths = {"owner", "reviewer"})
    List<FarmProfile> findAllByStatusOrderByUpdatedAtAsc(
            FarmProfile.FarmProfileStatus status
    );

    @EntityGraph(attributePaths = {"owner", "reviewer"})
    @Query("""
            select profile
            from FarmProfile profile
            where profile.id = :profileId
            """)
    Optional<FarmProfile> findReviewDetailById(@Param("profileId") Long profileId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select profile
            from FarmProfile profile
            join fetch profile.owner
            where profile.owner.id = :ownerId
            """)
    Optional<FarmProfile> findByOwnerIdForUpdate(@Param("ownerId") Long ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select profile
            from FarmProfile profile
            join fetch profile.owner
            where profile.id = :id
            """)
    Optional<FarmProfile> findByIdForUpdate(@Param("id") Long id);

    List<FarmProfile> findAllByStatusOrderByUpdatedAtDesc(FarmProfile.FarmProfileStatus status);

    long countByStatus(FarmProfile.FarmProfileStatus status);
}
