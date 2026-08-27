package chungbuk.cityfarmerplus.admin.work.service;

import chungbuk.cityfarmerplus.admin.work.dto.AttendanceCorrectionRequest;
import chungbuk.cityfarmerplus.admin.work.dto.WorkAssignmentCorrectionResponse;
import chungbuk.cityfarmerplus.application.entity.JobApplication;
import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;
import chungbuk.cityfarmerplus.jobposting.entity.JobPostingDetails;
import chungbuk.cityfarmerplus.jobposting.repository.JobPostingRepository;
import chungbuk.cityfarmerplus.work.entity.WorkAssignment;
import chungbuk.cityfarmerplus.work.entity.WorkAssignmentCorrection;
import chungbuk.cityfarmerplus.work.repository.WorkAssignmentCorrectionRepository;
import chungbuk.cityfarmerplus.work.repository.WorkAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminWorkAssignmentServiceTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long POSTING_ID = 10L;
    private static final Long ASSIGNMENT_ID = 20L;

    @Mock
    private WorkAssignmentRepository workAssignmentRepository;

    @Mock
    private WorkAssignmentCorrectionRepository correctionRepository;

    @Mock
    private JobPostingRepository jobPostingRepository;

    @Mock
    private UserRepository userRepository;

    @Test
    void correctingCompletedAttendanceToAbsentReopensCompletedPosting() {
        Fixture fixture = completedFixture();
        when(userRepository.findById(ADMIN_ID))
                .thenReturn(Optional.of(fixture.admin()));
        when(workAssignmentRepository.findByIdForUpdate(ASSIGNMENT_ID))
                .thenReturn(Optional.of(fixture.assignment()));
        when(jobPostingRepository.findByIdForUpdate(POSTING_ID))
                .thenReturn(Optional.of(fixture.posting()));
        when(correctionRepository.save(any(WorkAssignmentCorrection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WorkAssignmentCorrectionResponse response = service().correctAttendance(
                ADMIN_ID,
                ASSIGNMENT_ID,
                new AttendanceCorrectionRequest(
                        WorkAssignment.AttendanceStatus.ABSENT,
                        "실제 결근 확인"
                )
        );

        assertThat(fixture.assignment().getStatus())
                .isEqualTo(WorkAssignment.WorkStatus.NO_SHOW);
        assertThat(fixture.assignment().getAttendanceStatus())
                .isEqualTo(WorkAssignment.AttendanceStatus.ABSENT);
        assertThat(fixture.assignment().getJobApplication().getStatus())
                .isEqualTo(JobApplication.ApplicationStatus.NO_SHOW);
        assertThat(fixture.posting().getStatus())
                .isEqualTo(JobPosting.JobPostingStatus.CLOSED);
        assertThat(response.previousWorkStatus())
                .isEqualTo(WorkAssignment.WorkStatus.COMPLETED);
        assertThat(response.newWorkStatus())
                .isEqualTo(WorkAssignment.WorkStatus.NO_SHOW);
        verify(jobPostingRepository).findByIdForUpdate(POSTING_ID);
    }

    private AdminWorkAssignmentService service() {
        return new AdminWorkAssignmentService(
                workAssignmentRepository,
                correctionRepository,
                jobPostingRepository,
                userRepository
        );
    }

    private Fixture completedFixture() {
        User admin = User.registerCenterAdmin("admin_1", "encoded", "담당자");
        ReflectionTestUtils.setField(admin, "id", ADMIN_ID);
        User farmOwner = User.register(
                "farm_owner",
                "encoded",
                "농가",
                User.UserType.FARM
        );
        FarmProfile farm = FarmProfile.createDraft(
                farmOwner,
                "새봄농가",
                "김농부",
                "01012345678",
                "충북 청주시 상당구",
                ChungbukCityCounty.CHEONGJU,
                List.of("감자"),
                "감자를 재배합니다.",
                null
        );
        ReflectionTestUtils.setField(
                farm,
                "status",
                FarmProfile.FarmProfileStatus.APPROVED
        );
        ReflectionTestUtils.setField(farm, "id", 5L);
        JobPosting posting = JobPosting.createDraft(farm, details());
        posting.submitForReview(Instant.parse("2026-07-30T00:00:00Z"));
        posting.approve(Instant.parse("2026-07-31T00:00:00Z"));
        ReflectionTestUtils.setField(posting, "id", POSTING_ID);
        JobApplication application = JobApplication.apply(
                posting,
                User.register(
                        "urban_1",
                        "encoded",
                        "도시농부",
                        User.UserType.URBAN_FARMER
                ),
                Instant.parse("2026-08-01T00:00:00Z"),
                "CHEONGJU",
                "MONDAY",
                1
        );
        application.match(admin, Instant.parse("2026-08-02T00:00:00Z"));
        WorkAssignment assignment = WorkAssignment.fromMatchedApplication(application);
        ReflectionTestUtils.setField(assignment, "id", ASSIGNMENT_ID);
        assignment.recordAttendance(
                WorkAssignment.AttendanceStatus.PRESENT,
                farmOwner,
                Instant.parse("2026-08-20T00:00:00Z")
        );
        assignment.completeByFarm(Instant.parse("2026-08-20T07:00:00Z"));
        posting.close(Instant.parse("2026-08-10T00:00:00Z"));
        posting.markWorkCompleted(Instant.parse("2026-08-20T07:00:00Z"));
        return new Fixture(admin, assignment, posting);
    }

    private JobPostingDetails details() {
        return new JobPostingDetails(
                "감자",
                "수확",
                LocalDate.of(2026, 8, 20),
                LocalTime.of(9, 0),
                LocalTime.of(16, 0),
                2,
                "청주시 상당구 농장 입구",
                100_000,
                JobPosting.WageUnit.DAILY,
                "장갑",
                "물 충분히 마시기",
                "함께 일해요",
                "초보자 환영",
                "감자 수확 작업자를 모집합니다",
                "감자 수확을 함께할 분을 모집합니다.",
                "농가의 안내에 따라 작업해 주세요."
        );
    }

    private record Fixture(
            User admin,
            WorkAssignment assignment,
            JobPosting posting
    ) {
    }
}
