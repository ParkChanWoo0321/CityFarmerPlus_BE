package chungbuk.cityfarmerplus.common.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class FileDeletionWorker {

    private static final int BATCH_SIZE = 100;

    private final FileDeletionTaskRepository taskRepository;
    private final FileStorage fileStorage;

    @Scheduled(
            fixedDelayString = "${app.file-cleanup.fixed-delay:60000}",
            initialDelayString = "${app.file-cleanup.initial-delay:60000}"
    )
    @Transactional
    public void retryPendingTasks() {
        Instant now = Instant.now();
        taskRepository.findReadyForUpdate(now, PageRequest.of(0, BATCH_SIZE))
                .forEach(task -> delete(task, now));
    }

    private void delete(FileDeletionTask task, Instant now) {
        try {
            fileStorage.delete(task.getStorageKey());
            task.complete(now);
        } catch (RuntimeException exception) {
            task.retryAfterFailure(exception, now);
            log.warn(
                    "A withdrawn account file deletion will be retried. taskId={}, attempts={}",
                    task.getId(),
                    task.getAttempts()
            );
        }
    }
}
