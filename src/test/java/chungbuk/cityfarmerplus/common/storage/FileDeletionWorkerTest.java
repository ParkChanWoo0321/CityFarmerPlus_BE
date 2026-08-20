package chungbuk.cityfarmerplus.common.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileDeletionWorkerTest {

    @Mock
    private FileDeletionTaskRepository taskRepository;

    @Mock
    private FileStorage fileStorage;

    @Test
    void completedDeletionIsRecorded() {
        FileDeletionTask task = FileDeletionTask.accountWithdrawal(
                "education/user-1/file.pdf",
                Instant.now()
        );
        when(taskRepository.findReadyForUpdate(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(task));

        new FileDeletionWorker(taskRepository, fileStorage).retryPendingTasks();

        verify(fileStorage).delete("education/user-1/file.pdf");
        assertThat(task.getCompletedAt()).isNotNull();
        assertThat(task.getAttempts()).isZero();
    }

    @Test
    void failedDeletionIsScheduledForRetry() {
        Instant before = Instant.now();
        FileDeletionTask task = FileDeletionTask.accountWithdrawal(
                "farm/user-2/file.pdf",
                before
        );
        when(taskRepository.findReadyForUpdate(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(task));
        doThrow(new FileStorageException("temporary failure"))
                .when(fileStorage)
                .delete("farm/user-2/file.pdf");

        new FileDeletionWorker(taskRepository, fileStorage).retryPendingTasks();

        assertThat(task.getCompletedAt()).isNull();
        assertThat(task.getAttempts()).isEqualTo(1);
        assertThat(task.getNextAttemptAt()).isAfter(before);
        assertThat(task.getLastErrorType()).isEqualTo(FileStorageException.class.getName());
    }
}
