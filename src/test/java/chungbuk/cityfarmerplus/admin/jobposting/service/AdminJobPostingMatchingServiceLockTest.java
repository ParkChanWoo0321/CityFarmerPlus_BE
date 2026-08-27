package chungbuk.cityfarmerplus.admin.jobposting.service;

import chungbuk.cityfarmerplus.admin.jobposting.dto.JobPostingMatchRequest;
import chungbuk.cityfarmerplus.application.entity.JobApplication;
import chungbuk.cityfarmerplus.application.exception.JobApplicationException;
import chungbuk.cityfarmerplus.application.repository.JobApplicationRepository;
import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;
import chungbuk.cityfarmerplus.jobposting.repository.JobPostingRepository;
import chungbuk.cityfarmerplus.work.repository.WorkAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminJobPostingMatchingServiceLockTest {

    @Mock
    private JobPostingRepository jobPostingRepository;

    @Mock
    private JobApplicationRepository jobApplicationRepository;

    @Mock
    private WorkAssignmentRepository workAssignmentRepository;

    @Mock
    private UserRepository userRepository;

    @Test
    void locksUrbanFarmerBeforeCheckingCrossPostingOverlap() {
        long adminId = 1L;
        long postingId = 10L;
        long applicationId = 20L;
        long urbanFarmerId = 30L;
        User admin = User.registerCenterAdmin("admin", "encoded", "담당자");
        User urbanFarmer = mock(User.class);
        JobPosting posting = mock(JobPosting.class);
        JobApplication application = mock(JobApplication.class);

        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(jobPostingRepository.findByIdForUpdate(postingId))
                .thenReturn(Optional.of(posting));
        when(posting.getStatus()).thenReturn(JobPosting.JobPostingStatus.OPEN);
        when(posting.getCapacity()).thenReturn(2);
        when(posting.getWorkDate()).thenReturn(LocalDate.of(2026, 9, 1));
        when(posting.getStartTime()).thenReturn(LocalTime.of(9, 0));
        when(posting.getEndTime()).thenReturn(LocalTime.of(12, 0));
        when(jobApplicationRepository.findAllByIdForUpdate(Set.of(applicationId)))
                .thenReturn(List.of(application));
        when(application.getJobPosting()).thenReturn(posting);
        when(posting.getId()).thenReturn(postingId);
        when(application.getStatus())
                .thenReturn(JobApplication.ApplicationStatus.APPLIED);
        when(application.getUrbanFarmer()).thenReturn(urbanFarmer);
        when(urbanFarmer.getId()).thenReturn(urbanFarmerId);
        when(userRepository.findAllByIdForUpdate(Set.of(urbanFarmerId)))
                .thenReturn(List.of(urbanFarmer));
        when(jobApplicationRepository.findByJobPostingIdAndStatusForUpdate(
                postingId,
                JobApplication.ApplicationStatus.MATCHED
        )).thenReturn(List.of());
        when(workAssignmentRepository.findOverlappingAssignmentsForUpdate(
                urbanFarmerId,
                LocalDate.of(2026, 9, 1),
                LocalTime.of(9, 0),
                LocalTime.of(12, 0)
        )).thenReturn(List.of(mock(chungbuk.cityfarmerplus.work.entity.WorkAssignment.class)));

        assertThatThrownBy(() -> service().match(
                adminId,
                postingId,
                new JobPostingMatchRequest(List.of(applicationId))
        )).isInstanceOf(JobApplicationException.class);

        InOrder lockOrder = inOrder(userRepository, workAssignmentRepository);
        lockOrder.verify(userRepository).findAllByIdForUpdate(Set.of(urbanFarmerId));
        lockOrder.verify(workAssignmentRepository).findOverlappingAssignmentsForUpdate(
                urbanFarmerId,
                LocalDate.of(2026, 9, 1),
                LocalTime.of(9, 0),
                LocalTime.of(12, 0)
        );
        verify(jobApplicationRepository).findByJobPostingIdAndStatusForUpdate(
                postingId,
                JobApplication.ApplicationStatus.MATCHED
        );
    }

    private AdminJobPostingMatchingService service() {
        return new AdminJobPostingMatchingService(
                jobPostingRepository,
                jobApplicationRepository,
                workAssignmentRepository,
                userRepository
        );
    }
}
