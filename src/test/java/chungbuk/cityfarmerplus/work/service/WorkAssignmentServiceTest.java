package chungbuk.cityfarmerplus.work.service;

import chungbuk.cityfarmerplus.application.entity.JobApplication;
import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;
import chungbuk.cityfarmerplus.jobposting.repository.JobPostingRepository;
import chungbuk.cityfarmerplus.jobposting.service.JobPostingAccessService;
import chungbuk.cityfarmerplus.work.dto.WorkAssignmentView;
import chungbuk.cityfarmerplus.work.entity.WorkAssignment;
import chungbuk.cityfarmerplus.work.exception.WorkAssignmentException;
import chungbuk.cityfarmerplus.work.repository.WorkAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkAssignmentServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-13T03:30:00Z"),
            ZoneOffset.UTC
    );

    @Mock
    private WorkAssignmentRepository assignmentRepository;
    @Mock
    private JobPostingRepository postingRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JobPostingAccessService accessService;

    private WorkAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new WorkAssignmentService(
                assignmentRepository,
                postingRepository,
                userRepository,
                accessService,
                FIXED_CLOCK
        );
    }

    @Test
    void allViewKeepsLegacyDescendingTimelineOrder() {
        givenActiveUrbanFarmer(1L);
        var pageable = PageRequest.of(2, 10, Sort.by(
                Sort.Order.desc("workDate"),
                Sort.Order.desc("startTime"),
                Sort.Order.desc("id")
        ));
        when(assignmentRepository.findByUrbanFarmerId(1L, pageable))
                .thenReturn(Page.<WorkAssignment>empty(pageable));

        service.getUrbanFarmerAssignments(1L, WorkAssignmentView.ALL, 2, 10);

        verify(assignmentRepository).findByUrbanFarmerId(1L, pageable);
    }

    @Test
    void farmAssignmentsUseStableDescendingTimelineOrder() {
        FarmProfile farm = mock(FarmProfile.class);
        var pageable = PageRequest.of(1, 10, Sort.by(
                Sort.Order.desc("workDate"),
                Sort.Order.desc("startTime"),
                Sort.Order.desc("id")
        ));
        when(accessService.requireFarmProfile(1L)).thenReturn(farm);
        when(farm.getId()).thenReturn(5L);
        when(assignmentRepository.findByFarmProfileId(5L, pageable))
                .thenReturn(Page.empty(pageable));

        service.getFarmAssignments(1L, 1, 10);

        verify(assignmentRepository).findByFarmProfileId(5L, pageable);
    }

    @Test
    void upcomingViewUsesKstNowAndRepositoryChronologicalOrder() {
        givenActiveUrbanFarmer(1L);
        var pageable = PageRequest.of(0, 20);
        when(assignmentRepository.findTimelineUpcomingByUrbanFarmerId(
                1L,
                LocalDate.of(2026, 8, 13),
                LocalTime.of(12, 30),
                pageable
        )).thenReturn(Page.<WorkAssignment>empty(pageable));

        service.getUrbanFarmerAssignments(1L, WorkAssignmentView.UPCOMING, 0, 20);

        verify(assignmentRepository).findTimelineUpcomingByUrbanFarmerId(
                1L,
                LocalDate.of(2026, 8, 13),
                LocalTime.of(12, 30),
                pageable
        );
    }

    @Test
    void pastViewUsesKstNowAndRepositoryReverseChronologicalOrder() {
        givenActiveUrbanFarmer(1L);
        var pageable = PageRequest.of(1, 5);
        when(assignmentRepository.findTimelinePastByUrbanFarmerId(
                1L,
                LocalDate.of(2026, 8, 13),
                LocalTime.of(12, 30),
                pageable
        )).thenReturn(Page.<WorkAssignment>empty(pageable));

        service.getUrbanFarmerAssignments(1L, WorkAssignmentView.PAST, 1, 5);

        verify(assignmentRepository).findTimelinePastByUrbanFarmerId(
                1L,
                LocalDate.of(2026, 8, 13),
                LocalTime.of(12, 30),
                pageable
        );
    }

    @Test
    void attendanceBeforeWorkStartIsRejectedUsingInjectedKstClock() {
        FarmProfile farm = mock(FarmProfile.class);
        WorkAssignment assignment = mock(WorkAssignment.class);
        JobPosting posting = mock(JobPosting.class);

        when(accessService.requireFarmProfileForUpdate(1L)).thenReturn(farm);
        when(farm.getId()).thenReturn(5L);
        when(assignmentRepository.findById(10L)).thenReturn(Optional.of(assignment));
        when(assignmentRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(assignment));
        when(assignment.getFarmProfileId()).thenReturn(5L);
        when(assignment.getJobPostingId()).thenReturn(20L);
        when(assignment.getWorkDate()).thenReturn(LocalDate.of(2026, 8, 13));
        when(assignment.getStartTime()).thenReturn(LocalTime.of(12, 31));
        when(postingRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(posting));

        assertThatThrownBy(() -> service.recordAttendance(
                1L,
                10L,
                WorkAssignment.AttendanceStatus.PRESENT
        ))
                .isInstanceOf(WorkAssignmentException.class)
                .extracting("code")
                .isEqualTo("INVALID_WORK_ASSIGNMENT_STATE");

        verify(assignment, never()).recordAttendance(any(), any(), any());
    }

    @Test
    void attendanceAtWorkStartUsesInjectedClockInstant() {
        FarmProfile farm = mock(FarmProfile.class);
        WorkAssignment assignment = mock(WorkAssignment.class);
        JobPosting posting = mock(JobPosting.class);
        JobApplication application = mock(JobApplication.class);
        User urbanFarmer = mock(User.class);
        User farmOwner = mock(User.class);

        when(accessService.requireFarmProfileForUpdate(1L)).thenReturn(farm);
        when(farm.getId()).thenReturn(5L);
        when(farm.getOwner()).thenReturn(farmOwner);
        when(assignmentRepository.findById(10L)).thenReturn(Optional.of(assignment));
        when(assignmentRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(assignment));
        when(assignment.getFarmProfileId()).thenReturn(5L);
        when(assignment.getJobPostingId()).thenReturn(20L);
        when(assignment.getWorkDate()).thenReturn(LocalDate.of(2026, 8, 13));
        when(assignment.getStartTime()).thenReturn(LocalTime.of(12, 30));
        when(assignment.getJobApplication()).thenReturn(application);
        when(assignment.getUrbanFarmer()).thenReturn(urbanFarmer);
        when(application.getId()).thenReturn(30L);
        when(postingRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(posting));
        when(posting.getId()).thenReturn(20L);
        when(assignmentRepository.countByJobPostingIdAndStatus(
                20L,
                WorkAssignment.WorkStatus.SCHEDULED
        )).thenReturn(1L);

        service.recordAttendance(
                1L,
                10L,
                WorkAssignment.AttendanceStatus.PRESENT
        );

        verify(assignment).recordAttendance(
                WorkAssignment.AttendanceStatus.PRESENT,
                farmOwner,
                FIXED_CLOCK.instant()
        );
    }

    @Test
    void retryingSameAttendanceReturnsCurrentAssignmentWithoutStateTransition() {
        FarmProfile farm = mock(FarmProfile.class);
        WorkAssignment assignment = mock(WorkAssignment.class);
        JobPosting posting = mock(JobPosting.class);
        JobApplication application = mock(JobApplication.class);
        User urbanFarmer = mock(User.class);
        User farmOwner = mock(User.class);

        when(accessService.requireFarmProfileForUpdate(1L)).thenReturn(farm);
        when(farm.getId()).thenReturn(5L);
        when(farm.getOwner()).thenReturn(farmOwner);
        when(assignmentRepository.findById(10L)).thenReturn(Optional.of(assignment));
        when(assignmentRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(assignment));
        when(assignment.getFarmProfileId()).thenReturn(5L);
        when(assignment.getJobPostingId()).thenReturn(20L);
        when(assignment.getAttendanceStatus())
                .thenReturn(WorkAssignment.AttendanceStatus.ABSENT);
        when(assignment.getJobApplication()).thenReturn(application);
        when(assignment.getUrbanFarmer()).thenReturn(urbanFarmer);
        when(postingRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(posting));

        service.recordAttendance(
                1L,
                10L,
                WorkAssignment.AttendanceStatus.ABSENT
        );

        verify(assignment).recordAttendance(
                WorkAssignment.AttendanceStatus.ABSENT,
                farmOwner,
                FIXED_CLOCK.instant()
        );
        verify(assignmentRepository, never()).countByJobPostingIdAndStatus(
                any(),
                any()
        );
    }

    @Test
    void invalidAttendanceValueUsesWorkAssignmentErrorContract() {
        FarmProfile farm = mock(FarmProfile.class);
        WorkAssignment assignment = mock(WorkAssignment.class);
        JobPosting posting = mock(JobPosting.class);
        User farmOwner = mock(User.class);

        when(accessService.requireFarmProfileForUpdate(1L)).thenReturn(farm);
        when(farm.getId()).thenReturn(5L);
        when(farm.getOwner()).thenReturn(farmOwner);
        when(assignmentRepository.findById(10L)).thenReturn(Optional.of(assignment));
        when(assignmentRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(assignment));
        when(assignment.getFarmProfileId()).thenReturn(5L);
        when(assignment.getJobPostingId()).thenReturn(20L);
        when(assignment.getWorkDate()).thenReturn(LocalDate.of(2026, 8, 13));
        when(assignment.getStartTime()).thenReturn(LocalTime.of(12, 30));
        when(postingRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(posting));
        doThrow(new IllegalArgumentException("출근 또는 결근을 선택해야 합니다."))
                .when(assignment)
                .recordAttendance(
                        WorkAssignment.AttendanceStatus.NOT_RECORDED,
                        farmOwner,
                        FIXED_CLOCK.instant()
                );

        assertThatThrownBy(() -> service.recordAttendance(
                1L,
                10L,
                WorkAssignment.AttendanceStatus.NOT_RECORDED
        ))
                .isInstanceOf(WorkAssignmentException.class)
                .extracting("code")
                .isEqualTo("INVALID_WORK_ASSIGNMENT_STATE");
    }

    @Test
    void completionBeforeWorkEndIsRejectedUsingInjectedKstClock() {
        FarmProfile farm = mock(FarmProfile.class);
        WorkAssignment assignment = mock(WorkAssignment.class);
        JobPosting posting = mock(JobPosting.class);

        when(accessService.requireFarmProfileForUpdate(1L)).thenReturn(farm);
        when(farm.getId()).thenReturn(5L);
        when(assignmentRepository.findById(10L)).thenReturn(Optional.of(assignment));
        when(assignmentRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(assignment));
        when(assignment.getFarmProfileId()).thenReturn(5L);
        when(assignment.getJobPostingId()).thenReturn(20L);
        when(assignment.getWorkDate()).thenReturn(LocalDate.of(2026, 8, 13));
        when(assignment.getEndTime()).thenReturn(LocalTime.of(12, 31));
        when(postingRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(posting));

        assertThatThrownBy(() -> service.completeByFarm(1L, 10L))
                .isInstanceOf(WorkAssignmentException.class)
                .extracting("code")
                .isEqualTo("INVALID_WORK_ASSIGNMENT_STATE");

        verify(assignment, never()).completeByFarm(any());
    }

    @Test
    void completingLastAssignmentAlsoCompletesExpiredOpenPosting() {
        FarmProfile farm = mock(FarmProfile.class);
        WorkAssignment assignment = mock(WorkAssignment.class);
        JobPosting posting = mock(JobPosting.class);
        JobApplication application = mock(JobApplication.class);
        User urbanFarmer = mock(User.class);
        AtomicReference<JobPosting.JobPostingStatus> postingStatus =
                new AtomicReference<>(JobPosting.JobPostingStatus.OPEN);

        when(accessService.requireFarmProfileForUpdate(1L)).thenReturn(farm);
        when(farm.getId()).thenReturn(5L);
        when(assignmentRepository.findById(10L)).thenReturn(Optional.of(assignment));
        when(assignmentRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(assignment));
        when(assignment.getFarmProfileId()).thenReturn(5L);
        when(assignment.getJobPostingId()).thenReturn(20L);
        when(assignment.getWorkDate()).thenReturn(LocalDate.of(2026, 8, 13));
        when(assignment.getEndTime()).thenReturn(LocalTime.of(12, 30));
        when(assignment.getJobApplication()).thenReturn(application);
        when(assignment.getUrbanFarmer()).thenReturn(urbanFarmer);
        when(application.getId()).thenReturn(30L);
        when(postingRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(posting));
        when(posting.getId()).thenReturn(20L);
        when(posting.getStatus()).thenAnswer(ignored -> postingStatus.get());
        doAnswer(ignored -> {
            postingStatus.set(JobPosting.JobPostingStatus.CLOSED);
            return null;
        }).when(posting).close(any(Instant.class));
        doAnswer(ignored -> {
            postingStatus.set(JobPosting.JobPostingStatus.WORK_COMPLETED);
            return null;
        }).when(posting).markWorkCompleted(any(Instant.class));
        when(assignmentRepository.countByJobPostingIdAndStatus(
                20L,
                WorkAssignment.WorkStatus.SCHEDULED
        )).thenReturn(0L);

        service.completeByFarm(1L, 10L);

        verify(assignment).completeByFarm(FIXED_CLOCK.instant());
        verify(posting).close(FIXED_CLOCK.instant());
        verify(posting).markWorkCompleted(FIXED_CLOCK.instant());
    }

    private void givenActiveUrbanFarmer(Long userId) {
        User user = mock(User.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(user.isActive()).thenReturn(true);
        when(user.getUserType()).thenReturn(User.UserType.URBAN_FARMER);
    }
}
