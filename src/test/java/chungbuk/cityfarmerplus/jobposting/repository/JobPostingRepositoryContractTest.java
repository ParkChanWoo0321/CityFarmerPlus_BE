package chungbuk.cityfarmerplus.jobposting.repository;

import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class JobPostingRepositoryContractTest {

    @Test
    void postingMutationLookupUsesPessimisticWriteLock() throws Exception {
        Method method = JobPostingRepository.class.getMethod(
                "findByIdForUpdate",
                Long.class
        );

        Lock lock = method.getAnnotation(Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void reviewHistoryDeletionIsExplicitlyModifyingQuery() throws Exception {
        Method method = JobPostingReviewRepository.class.getMethod(
                "deleteAllByJobPostingId",
                Long.class
        );

        assertThat(method.getAnnotation(Modifying.class)).isNotNull();
    }
}
