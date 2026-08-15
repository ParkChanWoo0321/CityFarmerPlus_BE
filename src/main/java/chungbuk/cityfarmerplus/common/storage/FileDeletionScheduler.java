package chungbuk.cityfarmerplus.common.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
                        "Failed to delete a withdrawn account document. storageKey={}",
                        storageKey,
                        exception
                );
            }
        }
    }
}
