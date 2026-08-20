package chungbuk.cityfarmerplus.jobposting.service;

import chungbuk.cityfarmerplus.application.entity.JobApplication;
import chungbuk.cityfarmerplus.application.repository.JobApplicationRepository;
import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.common.exception.DomainException;
import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import chungbuk.cityfarmerplus.farm.repository.FarmProfileRepository;
import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;
import chungbuk.cityfarmerplus.jobposting.entity.JobPostingDetails;
import chungbuk.cityfarmerplus.jobposting.repository.JobPostingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FarmAccountStateCleanerTest {

    private static final Long USER_ID = 21L;

    @Mock
    private FarmProfileRepository farmProfileRepository;

    @Mock
    private JobPostingRepository postingRepository;

    @Mock
    private JobApplicationRepository applicationRepository;

    private FarmAccountStateCleaner cleaner;

    @BeforeEach
    void setUp() {
        cleaner = new FarmAccountStateCleaner(
                farmProfileRepository,
                postingRepository,
                applicationRepository
        );
    }

    @Test
    void matchedApplicationOnActivePostingBlocksWithdrawalWithoutChangingState() {
        FarmProfile profile = approvedFarm();
        JobPosting activePosting = openPosting(profile, 101L);
        when(farmProfileRepository.findByOwnerIdForUpdate(USER_ID))
                .thenReturn(Optional.of(profile));
        when(postingRepository.findAllByFarmOwnerIdForUpdate(USER_ID))
                .thenReturn(List.of(activePosting));
        when(applicationRepository.existsByJobPostingIdAndStatus(
                101L,
                JobApplication.ApplicationStatus.MATCHED
        )).thenReturn(true);

        assertThatThrownBy(() -> cleaner.clean(USER_ID))
                .isInstanceOfSatisfying(DomainException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("UPCOMING_WORK_EXISTS");
                });

        assertThat(activePosting.getStatus())
                .isEqualTo(JobPosting.JobPostingStatus.OPEN);
        assertThat(profile.getStatus())
                .isEqualTo(FarmProfile.FarmProfileStatus.APPROVED);
        verify(applicationRepository, never()).findByJobPostingIdAndStatus(
                101L,
                JobApplication.ApplicationStatus.APPLIED
        );
    }

    @Test
    void withdrawalCancelsUnfinishedPostingsAndAppliedApplicationsOnly() {
        FarmProfile profile = approvedFarm();
        JobPosting activePosting = openPosting(profile, 101L);
        JobPosting cancelledPosting = cancelledPosting(profile, 102L);
        JobPosting completedPosting = completedPosting(profile, 103L);
        User urbanFarmer = urbanFarmer();
        JobApplication applied = application(activePosting, urbanFarmer);
        JobApplication withdrawn = application(activePosting, urbanFarmer);
        withdrawn.withdraw(Instant.parse("2026-08-02T00:00:00Z"));
        JobApplication notMatched = application(activePosting, urbanFarmer);
        notMatched.markNotMatched();

        when(farmProfileRepository.findByOwnerIdForUpdate(USER_ID))
                .thenReturn(Optional.of(profile));
        when(postingRepository.findAllByFarmOwnerIdForUpdate(USER_ID))
                .thenReturn(List.of(activePosting, cancelledPosting, completedPosting));
        when(applicationRepository.existsByJobPostingIdAndStatus(
                101L,
                JobApplication.ApplicationStatus.MATCHED
        )).thenReturn(false);
        when(applicationRepository.findByJobPostingIdAndStatus(
                101L,
                JobApplication.ApplicationStatus.APPLIED
        )).thenReturn(List.of(applied));

        cleaner.clean(USER_ID);

        assertThat(activePosting.getStatus())
                .isEqualTo(JobPosting.JobPostingStatus.CANCELLED);
        assertThat(cancelledPosting.getStatus())
                .isEqualTo(JobPosting.JobPostingStatus.CANCELLED);
        assertThat(completedPosting.getStatus())
                .isEqualTo(JobPosting.JobPostingStatus.WORK_COMPLETED);
        assertThat(applied.getStatus())
                .isEqualTo(JobApplication.ApplicationStatus.POSTING_CANCELLED);
        assertThat(withdrawn.getStatus())
                .isEqualTo(JobApplication.ApplicationStatus.WITHDRAWN);
        assertThat(notMatched.getStatus())
                .isEqualTo(JobApplication.ApplicationStatus.NOT_MATCHED);
        assertThat(profile.getStatus())
                .isEqualTo(FarmProfile.FarmProfileStatus.INACTIVE);
        verify(applicationRepository, never()).findByJobPostingIdAndStatus(
                102L,
                JobApplication.ApplicationStatus.APPLIED
        );
        verify(applicationRepository, never()).findByJobPostingIdAndStatus(
                103L,
                JobApplication.ApplicationStatus.APPLIED
        );
    }

    private FarmProfile approvedFarm() {
        User owner = User.register(
                "farm_owner",
                "encoded",
                "농가",
                User.UserType.FARM
        );
        FarmProfile profile = FarmProfile.createDraft(
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
                profile,
                "status",
                FarmProfile.FarmProfileStatus.APPROVED
        );
        return profile;
    }

    private JobPosting openPosting(FarmProfile profile, Long id) {
        JobPosting posting = JobPosting.createDraft(profile, details());
        posting.submitForReview(Instant.parse("2026-08-01T00:00:00Z"));
        posting.approve(Instant.parse("2026-08-01T01:00:00Z"));
        ReflectionTestUtils.setField(posting, "id", id);
        return posting;
    }

    private JobPosting cancelledPosting(FarmProfile profile, Long id) {
        JobPosting posting = openPosting(profile, id);
        posting.cancel(Instant.parse("2026-08-02T00:00:00Z"));
        return posting;
    }

    private JobPosting completedPosting(FarmProfile profile, Long id) {
        JobPosting posting = openPosting(profile, id);
        posting.close(Instant.parse("2026-08-02T00:00:00Z"));
        posting.markWorkCompleted(Instant.parse("2026-08-20T08:00:00Z"));
        return posting;
    }

    private JobApplication application(JobPosting posting, User urbanFarmer) {
        return JobApplication.apply(
                posting,
                urbanFarmer,
                Instant.parse("2026-08-01T02:00:00Z"),
                "CHEONGJU",
                "MONDAY",
                1
        );
    }

    private User urbanFarmer() {
        return User.register(
                "urban_farmer",
                "encoded",
                "도시농부",
                User.UserType.URBAN_FARMER
        );
    }

    private JobPostingDetails details() {
        return new JobPostingDetails(
                "감자",
                "수확",
                LocalDate.of(2099, 8, 20),
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
}
