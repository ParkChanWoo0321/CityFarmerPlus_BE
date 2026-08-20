package chungbuk.cityfarmerplus.urbanfarmer.profile.repository;

import chungbuk.cityfarmerplus.urbanfarmer.profile.entity.UrbanFarmerProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UrbanFarmerProfileRepository
        extends JpaRepository<UrbanFarmerProfile, Long> {

    Optional<UrbanFarmerProfile> findByUrbanFarmerId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select profile from UrbanFarmerProfile profile "
            + "where profile.urbanFarmer.id = :userId")
    Optional<UrbanFarmerProfile> findByUrbanFarmerIdForUpdate(
            @Param("userId") Long userId
    );

    boolean existsByUrbanFarmerId(Long userId);
}
