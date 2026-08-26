package chungbuk.cityfarmerplus.jobposting.service;

import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobPostingSpecificationsTest {

    @Test
    @SuppressWarnings("unchecked")
    void allStatusOrderingEndsWithStableIdTieBreaker() {
        LocalDate today = LocalDate.of(2026, 8, 26);
        LocalTime now = LocalTime.of(12, 0);
        Root<JobPosting> root = mock(Root.class);
        CriteriaQuery<JobPosting> query = mock(CriteriaQuery.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        Path<JobPosting.JobPostingStatus> statusPath = mock(Path.class);
        Path<LocalDate> workDatePath = mock(Path.class);
        Path<LocalTime> startTimePath = mock(Path.class);
        Path<Instant> approvedAtPath = mock(Path.class);
        Path<Long> idPath = mock(Path.class);
        Predicate statusIsOpen = mock(Predicate.class);
        Predicate workDateAfterToday = mock(Predicate.class);
        Predicate workDateIsToday = mock(Predicate.class);
        Predicate startTimeAfterNow = mock(Predicate.class);
        Predicate startsLaterToday = mock(Predicate.class);
        Predicate startsAfterNow = mock(Predicate.class);
        Predicate accepting = mock(Predicate.class);
        CriteriaBuilder.Case<Integer> statusCase = mock(CriteriaBuilder.Case.class);
        Order statusOrder = mock(Order.class);
        Order workDateOrder = mock(Order.class);
        Order startTimeOrder = mock(Order.class);
        Order approvedAtOrder = mock(Order.class);
        Order idOrder = mock(Order.class);

        when(query.getResultType()).thenReturn(JobPosting.class);
        when(root.<JobPosting.JobPostingStatus>get("status")).thenReturn(statusPath);
        when(root.<LocalDate>get("workDate")).thenReturn(workDatePath);
        when(root.<LocalTime>get("startTime")).thenReturn(startTimePath);
        when(root.<Instant>get("approvedAt")).thenReturn(approvedAtPath);
        when(root.<Long>get("id")).thenReturn(idPath);
        when(builder.equal(statusPath, JobPosting.JobPostingStatus.OPEN))
                .thenReturn(statusIsOpen);
        when(builder.greaterThan(workDatePath, today))
                .thenReturn(workDateAfterToday);
        when(builder.equal(workDatePath, today)).thenReturn(workDateIsToday);
        when(builder.greaterThan(startTimePath, now))
                .thenReturn(startTimeAfterNow);
        when(builder.and(workDateIsToday, startTimeAfterNow))
                .thenReturn(startsLaterToday);
        when(builder.or(workDateAfterToday, startsLaterToday))
                .thenReturn(startsAfterNow);
        when(builder.and(statusIsOpen, startsAfterNow))
                .thenReturn(accepting);
        when(builder.<Integer>selectCase()).thenReturn(statusCase);
        when(statusCase.when(accepting, 0)).thenReturn(statusCase);
        when(statusCase.otherwise(1)).thenReturn(statusCase);
        when(builder.asc((Expression<?>) statusCase)).thenReturn(statusOrder);
        when(builder.asc(workDatePath)).thenReturn(workDateOrder);
        when(builder.asc(startTimePath)).thenReturn(startTimeOrder);
        when(builder.desc(approvedAtPath)).thenReturn(approvedAtOrder);
        when(builder.asc(idPath)).thenReturn(idOrder);

        JobPostingSpecifications.orderOpenFirst(today, now)
                .toPredicate(root, query, builder);

        verify(query).orderBy(
                statusOrder,
                workDateOrder,
                startTimeOrder,
                approvedAtOrder,
                idOrder
        );
    }
}
