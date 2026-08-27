package chungbuk.cityfarmerplus.work.repository;

import chungbuk.cityfarmerplus.work.entity.WorkAssignmentCorrection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkAssignmentCorrectionRepository
        extends JpaRepository<WorkAssignmentCorrection, Long> {

    List<WorkAssignmentCorrection> findAllByWorkAssignmentIdOrderByCorrectedAtDesc(
            Long workAssignmentId
    );
}
