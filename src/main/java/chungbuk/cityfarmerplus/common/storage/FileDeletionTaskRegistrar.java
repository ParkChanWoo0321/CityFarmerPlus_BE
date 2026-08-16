package chungbuk.cityfarmerplus.common.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class FileDeletionTaskRegistrar {

    private final FileDeletionTaskRepository taskRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void register(String storageKey, String reason, Instant now) {
        taskRepository.saveAndFlush(
                FileDeletionTask.pending(storageKey, reason, now)
        );
    }
}
