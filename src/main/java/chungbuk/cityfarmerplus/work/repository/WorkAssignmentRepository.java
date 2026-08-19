package chungbuk.cityfarmerplus.work.repository;

import chungbuk.cityfarmerplus.work.entity.WorkAssignment;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface WorkAssignmentRepository extends JpaRepository<WorkAssignment, Long> {

    Page<WorkAssignment> findByUrbanFarmerId(Long urbanFarmerId, Pageable pageable);

    @Query(
            value = """
                    select assignment
                    from WorkAssignment assignment
                    where assignment.urbanFarmer.id = :urbanFarmerId
                      and assignment.status = chungbuk.cityfarmerplus.work.entity.WorkAssignment.WorkStatus.SCHEDULED
                      and (
                            assignment.workDate > :today
                            or (assignment.workDate = :today and assignment.endTime > :now)
                      )
                    order by assignment.workDate asc,
                             assignment.startTime asc,
                             assignment.id asc
                    """,
            countQuery = """
                    select count(assignment)
                    from WorkAssignment assignment
                    where assignment.urbanFarmer.id = :urbanFarmerId
                      and assignment.status = chungbuk.cityfarmerplus.work.entity.WorkAssignment.WorkStatus.SCHEDULED
                      and (
                            assignment.workDate > :today
                            or (assignment.workDate = :today and assignment.endTime > :now)
                      )
                    """
    )
    Page<WorkAssignment> findTimelineUpcomingByUrbanFarmerId(
            @Param("urbanFarmerId") Long urbanFarmerId,
            @Param("today") LocalDate today,
            @Param("now") LocalTime now,
            Pageable pageable
    );

    @Query(
            value = """
                    select assignment
                    from WorkAssignment assignment
                    where assignment.urbanFarmer.id = :urbanFarmerId
                      and (
                            assignment.status in (
                                chungbuk.cityfarmerplus.work.entity.WorkAssignment.WorkStatus.COMPLETED,
                                chungbuk.cityfarmerplus.work.entity.WorkAssignment.WorkStatus.NO_SHOW,
                                chungbuk.cityfarmerplus.work.entity.WorkAssignment.WorkStatus.CANCELLED
                            )
                            or (
                                assignment.status = chungbuk.cityfarmerplus.work.entity.WorkAssignment.WorkStatus.SCHEDULED
                                and (
                                    assignment.workDate < :today
                                    or (assignment.workDate = :today and assignment.endTime <= :now)
                                )
                            )
                      )
                    order by assignment.workDate desc,
                             assignment.startTime desc,
                             assignment.id desc
                    """,
            countQuery = """
                    select count(assignment)
                    from WorkAssignment assignment
                    where assignment.urbanFarmer.id = :urbanFarmerId
                      and (
                            assignment.status in (
                                chungbuk.cityfarmerplus.work.entity.WorkAssignment.WorkStatus.COMPLETED,
                                chungbuk.cityfarmerplus.work.entity.WorkAssignment.WorkStatus.NO_SHOW,
                                chungbuk.cityfarmerplus.work.entity.WorkAssignment.WorkStatus.CANCELLED
                            )
                            or (
                                assignment.status = chungbuk.cityfarmerplus.work.entity.WorkAssignment.WorkStatus.SCHEDULED
                                and (
                                    assignment.workDate < :today
                                    or (assignment.workDate = :today and assignment.endTime <= :now)
                                )
                            )
                      )
                    """
    )
    Page<WorkAssignment> findTimelinePastByUrbanFarmerId(
            @Param("urbanFarmerId") Long urbanFarmerId,
            @Param("today") LocalDate today,
            @Param("now") LocalTime now,
            Pageable pageable
    );

    Page<WorkAssignment> findByFarmProfileId(Long farmProfileId, Pageable pageable);

    List<WorkAssignment> findByJobPostingIdOrderByCreatedAtAsc(Long jobPostingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select assignment from WorkAssignment assignment where assignment.id = :id")
    Optional<WorkAssignment> findByIdForUpdate(@Param("id") Long id);

    long countByJobPostingIdAndStatusNot(
            Long jobPostingId,
            WorkAssignment.WorkStatus status
    );

    long countByJobPostingIdAndStatus(
            Long jobPostingId,
            WorkAssignment.WorkStatus status
    );

    @Query("""
            select count(assignment)
            from WorkAssignment assignment
            where assignment.urbanFarmer.id = :urbanFarmerId
              and assignment.workDate = :workDate
              and assignment.status <> chungbuk.cityfarmerplus.work.entity.WorkAssignment.WorkStatus.CANCELLED
              and assignment.startTime < :endTime
              and assignment.endTime > :startTime
            """)
    long countOverlappingAssignments(
            @Param("urbanFarmerId") Long urbanFarmerId,
            @Param("workDate") LocalDate workDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime
    );

    long countByStatus(WorkAssignment.WorkStatus status);

    @Query("""
            select assignment
            from WorkAssignment assignment
            where assignment.urbanFarmer.id = :urbanFarmerId
              and assignment.status = chungbuk.cityfarmerplus.work.entity.WorkAssignment.WorkStatus.SCHEDULED
              and (
                    assignment.workDate > :today
                    or (assignment.workDate = :today and assignment.endTime > :now)
              )
            order by assignment.workDate asc, assignment.startTime asc
            """)
    List<WorkAssignment> findUpcomingByUrbanFarmerId(
            @Param("urbanFarmerId") Long urbanFarmerId,
            @Param("today") LocalDate today,
            @Param("now") LocalTime now,
            Pageable pageable
    );

    @Query("""
            select assignment
            from WorkAssignment assignment
            where assignment.farmProfileId = :farmProfileId
              and assignment.status = chungbuk.cityfarmerplus.work.entity.WorkAssignment.WorkStatus.SCHEDULED
              and (
                    assignment.workDate > :today
                    or (assignment.workDate = :today and assignment.endTime > :now)
              )
            order by assignment.workDate asc,
                     assignment.startTime asc,
                     assignment.id asc
            """)
    List<WorkAssignment> findUpcomingByFarmProfileId(
            @Param("farmProfileId") Long farmProfileId,
            @Param("today") LocalDate today,
            @Param("now") LocalTime now,
            Pageable pageable
    );
}
