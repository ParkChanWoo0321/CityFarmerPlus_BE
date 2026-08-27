package chungbuk.cityfarmerplus.admin.jobposting.service;

import chungbuk.cityfarmerplus.admin.jobposting.dto.AdminJobPostingUpdateRequest;
import chungbuk.cityfarmerplus.application.entity.JobApplication;
import chungbuk.cityfarmerplus.application.repository.JobApplicationRepository;
import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import chungbuk.cityfarmerplus.jobposting.dto.JobPostingReviewResponse;
import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;
import chungbuk.cityfarmerplus.jobposting.entity.JobPostingDetails;
import chungbuk.cityfarmerplus.jobposting.entity.JobPostingReview;
import chungbuk.cityfarmerplus.jobposting.exception.JobPostingException;
import chungbuk.cityfarmerplus.jobposting.repository.JobPostingRepository;
import chungbuk.cityfarmerplus.jobposting.repository.JobPostingReviewRepository;
import chungbuk.cityfarmerplus.jobposting.service.JobPostingResponseAssembler;
import chungbuk.cityfarmerplus.jobposting.service.JobPostingScheduleValidator;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminJobPostingServiceTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long POSTING_ID = 10L;

    @Mock
    private JobPostingRepository jobPostingRepository;

    @Mock
    private JobPostingReviewRepository jobPostingReviewRepository;

    @Mock
    private JobPostingResponseAssembler responseAssembler;

    @Mock
    private JobPostingScheduleValidator scheduleValidator;

    @Mock
    private JobApplicationRepository jobApplicationRepository;

    @Mock
    private WorkAssignmentRepository workAssignmentRepository;

    @Mock
    private UserRepository userRepository;

    @Test
    void closingPostingMarksEveryAppliedApplicationAsNotMatched() {
        User admin = centerAdmin();
        JobPosting posting = openPosting();
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
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(jobPostingRepository.findByIdForUpdate(POSTING_ID))
                .thenReturn(Optional.of(posting));
        when(jobApplicationRepository.findByJobPostingIdAndStatus(
                POSTING_ID,
                JobApplication.ApplicationStatus.APPLIED
        )).thenReturn(List.of(application));
        when(jobPostingReviewRepository.save(any(JobPostingReview.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        JobPostingReviewResponse response = service().close(ADMIN_ID, POSTING_ID);

        assertThat(posting.getStatus()).isEqualTo(JobPosting.JobPostingStatus.CLOSED);
        assertThat(application.getStatus())
                .isEqualTo(JobApplication.ApplicationStatus.NOT_MATCHED);
        assertThat(response.action()).isEqualTo(JobPostingReview.ReviewAction.CLOSED);
        verify(jobApplicationRepository).findByJobPostingIdAndStatus(
                POSTING_ID,
                JobApplication.ApplicationStatus.APPLIED
        );
    }

    @Test
    void updatingPostingWithMatchedApplicationIsRejected() {
        User admin = centerAdmin();
        JobPosting posting = openPosting();
        int originalCapacity = posting.getCapacity();
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(jobPostingRepository.findByIdForUpdate(POSTING_ID))
                .thenReturn(Optional.of(posting));
        when(jobApplicationRepository.countByJobPostingIdAndStatus(
                POSTING_ID,
                JobApplication.ApplicationStatus.MATCHED
        )).thenReturn(1L);

        assertThatThrownBy(() -> service().update(
                ADMIN_ID,
                POSTING_ID,
                updateRequest()
        )).isInstanceOf(JobPostingException.class)
                .extracting("code")
                .isEqualTo("MATCHED_POSTING_UPDATE_NOT_ALLOWED");

        assertThat(posting.getCapacity()).isEqualTo(originalCapacity);
        verify(jobPostingReviewRepository, never()).save(any(JobPostingReview.class));
    }

    private AdminJobPostingService service() {
        return new AdminJobPostingService(
                jobPostingRepository,
                jobPostingReviewRepository,
                responseAssembler,
                scheduleValidator,
                jobApplicationRepository,
                workAssignmentRepository,
                userRepository
        );
    }

    private User centerAdmin() {
        User admin = User.registerCenterAdmin("admin_1", "encoded", "담당자");
        ReflectionTestUtils.setField(admin, "id", ADMIN_ID);
        return admin;
    }

    private JobPosting openPosting() {
        User owner = User.register("farm_owner", "encoded", "농가", User.UserType.FARM);
        FarmProfile farm = FarmProfile.createDraft(
                owner,
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
        JobPosting posting = JobPosting.createDraft(farm, details());
        posting.submitForReview(Instant.parse("2026-07-30T00:00:00Z"));
        posting.approve(Instant.parse("2026-07-31T00:00:00Z"));
        ReflectionTestUtils.setField(posting, "id", POSTING_ID);
        return posting;
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

    private AdminJobPostingUpdateRequest updateRequest() {
        return new AdminJobPostingUpdateRequest(
                "근무 조건 변경",
                "감자",
                "선별",
                LocalDate.of(2026, 8, 21),
                LocalTime.of(10, 0),
                LocalTime.of(17, 0),
                3,
                "청주시 상당구 농장 창고",
                120_000,
                JobPosting.WageUnit.DAILY,
                "장갑",
                "휴식 시간 준수",
                "함께 일해요",
                "경험자 우대",
                "감자 선별 작업자를 모집합니다",
                "감자 선별을 함께할 분을 모집합니다.",
                "농가의 안내에 따라 작업해 주세요."
        );
    }
}
