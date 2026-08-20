package chungbuk.cityfarmerplus.jobposting.repository;

import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Collection;
import java.time.LocalDate;
import java.time.LocalTime;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long>,
        JpaSpecificationExecutor<JobPosting> {

    Page<JobPosting> findByFarmProfileOwnerId(Long ownerId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select posting from JobPosting posting where posting.id = :id")
    Optional<JobPosting> findByIdForUpdate(@Param("id") Long id);

    long countByFarmProfileOwnerIdAndStatus(
            Long ownerId,
            JobPosting.JobPostingStatus status
    );

    long countByStatus(JobPosting.JobPostingStatus status);

    @Query("""
            select count(posting)
            from JobPosting posting
            where posting.status = chungbuk.cityfarmerplus.jobposting.entity.JobPosting.JobPostingStatus.OPEN
              and (
                    posting.workDate > :today
                    or (posting.workDate = :today and posting.startTime > :now)
              )
            """)
    long countCurrentlyOpen(
            @Param("today") LocalDate today,
            @Param("now") LocalTime now
    );

    @Query("""
            select count(posting)
            from JobPosting posting
            where posting.farmProfile.owner.id = :ownerId
              and posting.status = chungbuk.cityfarmerplus.jobposting.entity.JobPosting.JobPostingStatus.OPEN
              and (
                    posting.workDate > :today
                    or (posting.workDate = :today and posting.startTime > :now)
              )
            """)
    long countCurrentlyOpenByFarmOwnerId(
            @Param("ownerId") Long ownerId,
            @Param("today") LocalDate today,
            @Param("now") LocalTime now
    );

    @Query("""
            select count(posting)
            from JobPosting posting
            where posting.farmProfile.owner.id = :ownerId
              and posting.status = chungbuk.cityfarmerplus.jobposting.entity.JobPosting.JobPostingStatus.OPEN
              and (
                    posting.workDate < :today
                    or (posting.workDate = :today and posting.startTime <= :now)
              )
            """)
    long countExpiredOpenByFarmOwnerId(
            @Param("ownerId") Long ownerId,
            @Param("today") LocalDate today,
            @Param("now") LocalTime now
    );

    List<JobPosting> findTop5ByStatusOrderByApprovedAtDesc(
            JobPosting.JobPostingStatus status
    );

    List<JobPosting> findTop5ByFarmProfileOwnerIdOrderByUpdatedAtDesc(Long ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select posting
            from JobPosting posting
            where posting.farmProfile.owner.id = :ownerId
            order by posting.id
            """)
    List<JobPosting> findAllByFarmOwnerIdForUpdate(@Param("ownerId") Long ownerId);

    boolean existsByFarmProfileOwnerIdAndStatusNotIn(
            Long ownerId,
            Collection<JobPosting.JobPostingStatus> statuses
    );
}
