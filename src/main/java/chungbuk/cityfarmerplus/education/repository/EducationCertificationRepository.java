package chungbuk.cityfarmerplus.education.repository;

import chungbuk.cityfarmerplus.education.entity.EducationCertification;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EducationCertificationRepository
        extends JpaRepository<EducationCertification, Long> {

    Optional<EducationCertification> findByUrbanFarmerId(Long userId);

    boolean existsByUrbanFarmerIdAndStatus(
            Long userId,
            EducationCertification.CertificationStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select certification
            from EducationCertification certification
            where certification.urbanFarmer.id = :userId
            """)
    Optional<EducationCertification> findByUrbanFarmerIdForUpdate(
            @Param("userId") Long userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select certification from EducationCertification certification where certification.id = :id")
    Optional<EducationCertification> findByIdForUpdate(@Param("id") Long id);
}
