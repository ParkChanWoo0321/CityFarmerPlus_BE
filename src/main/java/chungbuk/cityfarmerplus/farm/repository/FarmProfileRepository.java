package chungbuk.cityfarmerplus.farm.repository;

import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FarmProfileRepository extends JpaRepository<FarmProfile, Long> {

    boolean existsByOwnerId(Long ownerId);

    Optional<FarmProfile> findByOwnerId(Long ownerId);
}
