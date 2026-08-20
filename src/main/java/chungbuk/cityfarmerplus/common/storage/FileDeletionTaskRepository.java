package chungbuk.cityfarmerplus.common.storage;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface FileDeletionTaskRepository
        extends JpaRepository<FileDeletionTask, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select task
            from FileDeletionTask task
            where task.completedAt is null
              and task.nextAttemptAt <= :now
            order by task.id
            """)
    List<FileDeletionTask> findReadyForUpdate(
            @Param("now") Instant now,
            Pageable pageable
    );
}
