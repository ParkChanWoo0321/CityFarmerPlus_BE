package chungbuk.cityfarmerplus.work.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class WorkAssignmentRepositoryContractTest {

    @Test
    void farmHomeUpcomingQueryExcludesAlreadyEndedTodayAssignments()
            throws NoSuchMethodException {
        Method method = WorkAssignmentRepository.class.getMethod(
                "findUpcomingByFarmProfileId",
                Long.class,
                LocalDate.class,
                LocalTime.class,
                Pageable.class
        );

        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();
        assertThat(query.value())
                .contains("WorkStatus.SCHEDULED")
                .contains("assignment.workDate > :today")
                .contains("assignment.workDate = :today and assignment.endTime > :now")
                .contains("assignment.workDate asc")
                .contains("assignment.startTime asc");
    }
}
