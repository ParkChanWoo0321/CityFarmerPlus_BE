package chungbuk.cityfarmerplus.common.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FileDeletionSchedulerTest {

    @Mock
    private FileStorage fileStorage;

    @Mock
    private FileDeletionTaskRepository taskRepository;

    private FileDeletionScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new FileDeletionScheduler(fileStorage, taskRepository);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void withdrawalFileDeletionRunsOnlyAfterCommit() {
        scheduler.deleteAfterCommit(List.of("education/user-1/document.pdf"));

        verify(fileStorage, never()).delete("education/user-1/document.pdf");
        synchronization().afterCommit();

        verify(fileStorage).delete("education/user-1/document.pdf");
    }

    @Test
    void uploadedFileIsDeletedWhenOuterTransactionRollsBack() {
        scheduler.deleteOnRollback(List.of("farm/profile-1/document.pdf"));

        synchronization().afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(fileStorage).delete("farm/profile-1/document.pdf");
    }

    @Test
    void uploadedFileIsKeptWhenOuterTransactionCommits() {
        scheduler.deleteOnRollback(List.of("farm/profile-1/document.pdf"));

        synchronization().afterCompletion(TransactionSynchronization.STATUS_COMMITTED);

        verify(fileStorage, never()).delete("farm/profile-1/document.pdf");
    }

    private TransactionSynchronization synchronization() {
        return TransactionSynchronizationManager.getSynchronizations().get(0);
    }
}
