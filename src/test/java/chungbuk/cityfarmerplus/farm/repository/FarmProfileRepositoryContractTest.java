package chungbuk.cityfarmerplus.farm.repository;

import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FarmProfileRepositoryContractTest {

    @Test
    void reviewReadsFetchOwnerAndReviewer() throws Exception {
        Method list = FarmProfileRepository.class.getMethod(
                "findAllByStatusOrderByUpdatedAtAsc",
                FarmProfile.FarmProfileStatus.class
        );
        Method detail = FarmProfileRepository.class.getMethod(
                "findReviewDetailById",
                Long.class
        );

        assertReviewEntityGraph(list);
        assertReviewEntityGraph(detail);
        assertThat(list.getReturnType()).isEqualTo(List.class);

        Query query = detail.getAnnotation(Query.class);
        Param param = detail.getParameters()[0].getAnnotation(Param.class);

        assertThat(query).isNotNull();
        assertThat(query.value()).contains("profile.id = :profileId");
        assertThat(param).isNotNull();
        assertThat(param.value()).isEqualTo("profileId");
    }

    @Test
    void reviewMutationLookupUsesPessimisticWriteLock() throws Exception {
        Method method = FarmProfileRepository.class.getMethod(
                "findByIdForUpdate",
                Long.class
        );

        Lock lock = method.getAnnotation(Lock.class);
        Query query = method.getAnnotation(Query.class);
        Param param = method.getParameters()[0].getAnnotation(Param.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(query).isNotNull();
        assertThat(query.value())
                .contains("join fetch profile.owner")
                .contains("left join fetch profile.reviewer")
                .contains("profile.id = :profileId");
        assertThat(param).isNotNull();
        assertThat(param.value()).isEqualTo("profileId");
    }

    @Test
    void reviewCountUsesFarmProfileStatus() throws Exception {
        Method method = FarmProfileRepository.class.getMethod(
                "countByStatus",
                FarmProfile.FarmProfileStatus.class
        );

        assertThat(method.getReturnType()).isEqualTo(long.class);
    }

    @Test
    void adminListsFetchOwnerAndReviewerForAllAndFilteredQueries() throws Exception {
        Method all = FarmProfileRepository.class.getMethod(
                "findAllByOrderByUpdatedAtDesc"
        );
        Method filtered = FarmProfileRepository.class.getMethod(
                "findAllByStatusOrderByUpdatedAtDesc",
                FarmProfile.FarmProfileStatus.class
        );

        assertReviewEntityGraph(all);
        assertReviewEntityGraph(filtered);
        assertThat(all.getReturnType()).isEqualTo(List.class);
        assertThat(filtered.getReturnType()).isEqualTo(List.class);
    }

    private void assertReviewEntityGraph(Method method) {
        EntityGraph graph = method.getAnnotation(EntityGraph.class);

        assertThat(graph).isNotNull();
        assertThat(graph.attributePaths())
                .containsExactlyInAnyOrder("owner", "reviewer");
    }
}
