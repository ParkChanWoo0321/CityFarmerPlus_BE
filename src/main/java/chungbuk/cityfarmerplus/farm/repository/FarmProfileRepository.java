package chungbuk.cityfarmerplus.farm.repository;

import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FarmProfileRepository extends JpaRepository<FarmProfile, Long> {

    boolean existsByOwnerId(Long ownerId);

    Optional<FarmProfile> findByOwnerId(Long ownerId);

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
}
