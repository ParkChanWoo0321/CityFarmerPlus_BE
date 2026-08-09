package chungbuk.cityfarmerplus.urbanfarmer.repository;

import chungbuk.cityfarmerplus.urbanfarmer.entity.UrbanFarmerProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UrbanFarmerProfileRepository extends JpaRepository<UrbanFarmerProfile, Long> {
    Optional<UrbanFarmerProfile> findByUserId(Long userId);
    boolean existsByUserId(Long userId);

    @Override
    @EntityGraph(attributePaths = "user")
    Page<UrbanFarmerProfile> findAll(Pageable pageable);
}
