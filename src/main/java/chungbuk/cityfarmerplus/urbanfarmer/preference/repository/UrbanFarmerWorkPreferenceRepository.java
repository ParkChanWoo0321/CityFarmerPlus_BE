package chungbuk.cityfarmerplus.urbanfarmer.preference.repository;

import chungbuk.cityfarmerplus.urbanfarmer.preference.entity.UrbanFarmerWorkPreference;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UrbanFarmerWorkPreferenceRepository
        extends JpaRepository<UrbanFarmerWorkPreference, Long> {

    Optional<UrbanFarmerWorkPreference> findByUrbanFarmerId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select preference from UrbanFarmerWorkPreference preference "
            + "where preference.urbanFarmer.id = :userId")
    Optional<UrbanFarmerWorkPreference> findByUrbanFarmerIdForUpdate(
            @Param("userId") Long userId
    );
}
