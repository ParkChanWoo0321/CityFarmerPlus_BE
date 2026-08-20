package chungbuk.cityfarmerplus.common.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.List;
import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class FileDeletionScheduler {

    private final FileStorage fileStorage;
    private final FileDeletionTaskRepository taskRepository;
    private final FileDeletionTaskRegistrar taskRegistrar;

    public void deleteAfterCommit(Collection<String> storageKeys) {
        List<String> keys = normalize(storageKeys);
        if (keys.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        taskRepository.saveAll(
                keys.stream()
                        .map(key -> FileDeletionTask.accountWithdrawal(key, now))
                        .toList()
        );

        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            deleteQuietly(keys);
                        }
                    }
            );
            return;
        }
        deleteQuietly(keys);
    }

    public void deleteOnRollback(Collection<String> storageKeys) {
        List<String> keys = normalize(storageKeys);
        if (keys.isEmpty()
                || !TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != TransactionSynchronization.STATUS_COMMITTED) {
                            deleteQuietly(keys);
                        }
                    }
                }
        );
    }

    /**
     * 이미 원래 트랜잭션이 끝난 업로드 보상 경로에서 삭제 작업을 먼저 남기고
     * 즉시 삭제를 시도한다. 즉시 삭제가 실패해도 worker가 다시 처리한다.
     */
    public void deleteNowWithRetry(
            Collection<String> storageKeys,
            String reason
    ) {
        List<String> keys = normalize(storageKeys);
        if (keys.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        for (String key : keys) {
            try {
                taskRegistrar.register(key, reason, now);
            } catch (DataIntegrityViolationException duplicateTask) {
                log.debug("A file deletion task already exists. storageKey={}", key);
            } catch (RuntimeException exception) {
                log.error(
                        "Failed to register a compensating file deletion. storageKey={}",
                        key,
                        exception
                );
            }
            try {
                fileStorage.delete(key);
            } catch (RuntimeException exception) {
                log.warn(
                        "Compensating file deletion will be retried. storageKey={}",
                        key,
                        exception
                );
            }
        }
    }

    private List<String> normalize(Collection<String> storageKeys) {
        return storageKeys.stream()
                .filter(key -> key != null && !key.isBlank())
                .distinct()
                .toList();
    }

    private void deleteQuietly(List<String> storageKeys) {
        for (String storageKey : storageKeys) {
            try {
                fileStorage.delete(storageKey);
            } catch (RuntimeException exception) {
                log.error(
                        "Failed to delete a scheduled file. storageKey={}",
                        storageKey,
                        exception
                );
            }
        }
    }
}
