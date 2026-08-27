package chungbuk.cityfarmerplus.education.progress.repository;

import chungbuk.cityfarmerplus.education.progress.entity.EducationEnrollment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EducationEnrollmentRepository
        extends JpaRepository<EducationEnrollment, Long> {

    @Query("""
            select enrollment
            from EducationEnrollment enrollment
            where enrollment.urbanFarmer.id = :urbanFarmerId
              and enrollment.course.id in :courseIds
            """)
    List<EducationEnrollment> findAllForProgress(
            @Param("urbanFarmerId") Long urbanFarmerId,
            @Param("courseIds") Collection<Long> courseIds
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select enrollment
            from EducationEnrollment enrollment
            where enrollment.urbanFarmer.id = :urbanFarmerId
              and enrollment.course.id = :courseId
            """)
    Optional<EducationEnrollment> findForUpdate(
            @Param("urbanFarmerId") Long urbanFarmerId,
            @Param("courseId") Long courseId
    );

    Optional<EducationEnrollment> findByProviderAndExternalEnrollmentId(
            String provider,
            String externalEnrollmentId
    );
}
