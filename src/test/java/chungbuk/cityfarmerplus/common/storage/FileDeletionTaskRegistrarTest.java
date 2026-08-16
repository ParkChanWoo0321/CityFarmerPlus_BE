package chungbuk.cityfarmerplus.common.storage;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FileDeletionTaskRegistrarTest {

    @Test
    void registersPendingDeletionTask() {
        FileDeletionTaskRepository repository =
                mock(FileDeletionTaskRepository.class);
        FileDeletionTaskRegistrar registrar =
                new FileDeletionTaskRegistrar(repository);
        Instant now = Instant.parse("2026-08-17T00:00:00Z");

        registrar.register(
                "education/user-1/orphan.pdf",
                "EDUCATION_UPLOAD_COMPENSATION",
                now
        );

        var taskCaptor = org.mockito.ArgumentCaptor
                .forClass(FileDeletionTask.class);
        verify(repository).saveAndFlush(taskCaptor.capture());
        FileDeletionTask task = taskCaptor.getValue();
        assertThat(task.getStorageKey())
                .isEqualTo("education/user-1/orphan.pdf");
        assertThat(task.getReason())
                .isEqualTo("EDUCATION_UPLOAD_COMPENSATION");
        assertThat(task.getNextAttemptAt()).isEqualTo(now);
    }

    @Test
    void registrationUsesIndependentTransaction() throws Exception {
        Method method = FileDeletionTaskRegistrar.class.getMethod(
                "register",
                String.class,
                String.class,
                Instant.class
        );

        Transactional transactional =
                method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation())
                .isEqualTo(Propagation.REQUIRES_NEW);
    }
}
