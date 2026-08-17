package chungbuk.cityfarmerplus.jobposting.entity;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobPostingTest {

    @Test
    void followsDraftReviewOpenAndRejectedToDraftTransitions() {
        JobPosting posting = JobPosting.createDraft(approvedFarm(), details());

        posting.submitForReview(Instant.parse("2026-08-09T00:00:00Z"));
        assertThat(posting.getStatus()).isEqualTo(JobPosting.JobPostingStatus.PENDING_REVIEW);

        posting.reject();
        assertThat(posting.getStatus()).isEqualTo(JobPosting.JobPostingStatus.DRAFT);

        posting.submitForReview(Instant.parse("2026-08-09T00:10:00Z"));
        posting.approve(Instant.parse("2026-08-09T00:20:00Z"));

        assertThat(posting.getStatus()).isEqualTo(JobPosting.JobPostingStatus.OPEN);
        assertThat(posting.isVisibleToUrbanFarmers()).isTrue();
    }

    @Test
    void onlyApprovedFarmCanCreatePosting() {
        FarmProfile draft = draftFarm();

        assertThatThrownBy(() -> JobPosting.createDraft(draft, details()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("승인된 농가");
    }

    @Test
    void closesAutomaticallyOnlyWhenMatchedCapacityIsReached() {
        JobPosting posting = JobPosting.createDraft(approvedFarm(), details());
        Instant now = Instant.parse("2026-08-09T00:00:00Z");
        posting.submitForReview(now);
        posting.approve(now.plusSeconds(60));

        posting.closeWhenCapacityReached(1, now.plusSeconds(120));
        assertThat(posting.getStatus()).isEqualTo(JobPosting.JobPostingStatus.OPEN);

        posting.closeWhenCapacityReached(2, now.plusSeconds(180));
        assertThat(posting.getStatus()).isEqualTo(JobPosting.JobPostingStatus.CLOSED);
    }

    @Test
    void editingRejectedDraftClearsPreviousReviewRequestMarker() {
        JobPosting posting = JobPosting.createDraft(approvedFarm(), details());
        posting.submitForReview(Instant.parse("2026-08-09T00:00:00Z"));
        posting.reject();

        posting.updateDraft(details());

        assertThat(posting.getStatus()).isEqualTo(JobPosting.JobPostingStatus.DRAFT);
        assertThat(posting.getReviewRequestedAt()).isNull();
    }

    @Test
    void rejectsInvalidDetailsWithoutDependingOnWebValidation() {
        assertThatThrownBy(() -> new JobPostingDetails(
                " ",
                "수확",
                LocalDate.of(2026, 8, 20),
                LocalTime.of(9, 0),
                LocalTime.of(16, 0),
                2,
                "농장 입구",
                100_000,
                JobPosting.WageUnit.DAILY,
                null,
                null,
                null,
                null,
                "제목",
                "설명",
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("작물");

        assertThatThrownBy(() -> new JobPostingDetails(
                "감자",
                "수확",
                LocalDate.of(2026, 8, 20),
                LocalTime.of(9, 0),
                LocalTime.of(16, 0),
                0,
                "농장 입구",
                100_000,
                JobPosting.WageUnit.DAILY,
                null,
                null,
                null,
                null,
                "제목",
                "설명",
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("모집 인원");

        assertThatThrownBy(() -> new JobPostingDetails(
                "감자",
                "수확",
                LocalDate.of(2026, 8, 20),
                LocalTime.of(16, 0),
                LocalTime.of(9, 0),
                2,
                "농장 입구",
                100_000,
                JobPosting.WageUnit.DAILY,
                null,
                null,
                null,
                null,
                "제목",
                "설명",
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("종료 시간");
    }

    @Test
    void rejectsMissingTransitionTimestamps() {
        JobPosting posting = JobPosting.createDraft(approvedFarm(), details());

        assertThatThrownBy(() -> posting.submitForReview(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("심사 요청 시각");

        posting.submitForReview(Instant.parse("2026-08-09T00:00:00Z"));
        assertThatThrownBy(() -> posting.approve(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("승인 시각");

        posting.approve(Instant.parse("2026-08-09T00:10:00Z"));
        assertThatThrownBy(() -> posting.close(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("마감 시각");
    }

    @Test
    void completesAndReopensPostingAfterAttendanceCorrection() {
        JobPosting posting = JobPosting.createDraft(approvedFarm(), details());
        posting.submitForReview(Instant.parse("2026-08-09T00:00:00Z"));
        posting.approve(Instant.parse("2026-08-09T00:10:00Z"));
        posting.close(Instant.parse("2026-08-09T00:20:00Z"));

        posting.markWorkCompleted(Instant.parse("2026-08-20T08:00:00Z"));
        assertThat(posting.getStatus())
                .isEqualTo(JobPosting.JobPostingStatus.WORK_COMPLETED);

        posting.reopenAfterAttendanceCorrection();
        assertThat(posting.getStatus())
                .isEqualTo(JobPosting.JobPostingStatus.CLOSED);
    }

    @Test
    void acceptingApplicationsRequiresFutureStartAndActiveApprovedFarm() {
        FarmProfile farm = approvedFarm();
        JobPosting posting = JobPosting.createDraft(farm, details());
        posting.submitForReview(Instant.parse("2026-08-09T00:00:00Z"));
        posting.approve(Instant.parse("2026-08-09T00:10:00Z"));

        assertThat(posting.isAcceptingApplications(
                LocalDate.of(2026, 8, 20),
                LocalTime.of(8, 59)
        )).isTrue();
        assertThat(posting.isAcceptingApplications(
                LocalDate.of(2026, 8, 20),
                LocalTime.of(9, 0)
        )).isFalse();

        farm.getOwner().withdraw();
        assertThat(posting.isAcceptingApplications(
                LocalDate.of(2026, 8, 19),
                LocalTime.NOON
        )).isFalse();
    }

    private FarmProfile approvedFarm() {
        FarmProfile farm = draftFarm();
        ReflectionTestUtils.setField(
                farm,
                "status",
                FarmProfile.FarmProfileStatus.APPROVED
        );
        return farm;
    }

    private FarmProfile draftFarm() {
        User owner = User.register("farm_owner", "encoded", "농가", User.UserType.FARM);
        return FarmProfile.createDraft(
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
}
