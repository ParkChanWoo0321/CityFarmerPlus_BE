package chungbuk.cityfarmerplus.application.repository;

import chungbuk.cityfarmerplus.application.entity.JobApplication;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    Optional<JobApplication> findByJobPostingIdAndUrbanFarmerId(
            Long jobPostingId,
            Long urbanFarmerId
    );

    List<JobApplication> findAllByJobPostingIdInAndUrbanFarmerId(
            Collection<Long> jobPostingIds,
            Long urbanFarmerId
    );

    Page<JobApplication> findByUrbanFarmerId(Long urbanFarmerId, Pageable pageable);

    List<JobApplication> findByJobPostingIdOrderByCreatedAtAsc(Long jobPostingId);

    List<JobApplication> findByJobPostingIdAndStatus(
            Long jobPostingId,
            JobApplication.ApplicationStatus status
    );

    long countByJobPostingIdAndStatus(
            Long jobPostingId,
            JobApplication.ApplicationStatus status
    );

    boolean existsByJobPostingIdAndStatus(
            Long jobPostingId,
            JobApplication.ApplicationStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select application
            from JobApplication application
            where application.id in :ids
            order by application.id
            """)
    List<JobApplication> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select application from JobApplication application where application.id = :id")
    Optional<JobApplication> findByIdForUpdate(@Param("id") Long id);

    long countByStatus(JobApplication.ApplicationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select application
            from JobApplication application
            where application.urbanFarmer.id = :userId
            order by application.id
            """)
    List<JobApplication> findAllByUrbanFarmerIdForUpdate(
            @Param("userId") Long userId
    );
}
