package chungbuk.cityfarmerplus.urbanfarmer.participation.repository;

import chungbuk.cityfarmerplus.urbanfarmer.participation.entity.ParticipationApplication;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ParticipationApplicationRepository
        extends JpaRepository<ParticipationApplication, Long> {

    boolean existsByUrbanFarmerIdAndProgramYear(Long userId, int programYear);

    long countByStatus(ParticipationApplication.ParticipationStatus status);

    Optional<ParticipationApplication> findByIdAndUrbanFarmerId(
            Long id,
            Long userId
    );

    Optional<ParticipationApplication> findByUrbanFarmerIdAndProgramYear(
            Long userId,
            int programYear
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select application from ParticipationApplication application "
            + "where application.urbanFarmer.id = :userId "
            + "and application.programYear = :programYear")
    Optional<ParticipationApplication> findByUrbanFarmerIdAndProgramYearForUpdate(
            @Param("userId") Long userId,
            @Param("programYear") int programYear
    );

    List<ParticipationApplication> findAllByUrbanFarmerIdOrderByCreatedAtDesc(
            Long userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select application from ParticipationApplication application "
            + "where application.urbanFarmer.id = :userId "
            + "order by application.id")
    List<ParticipationApplication> findAllByUrbanFarmerIdForUpdate(
            @Param("userId") Long userId
    );

}
