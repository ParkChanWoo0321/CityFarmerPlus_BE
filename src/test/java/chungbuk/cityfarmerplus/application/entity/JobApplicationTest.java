package chungbuk.cityfarmerplus.application.entity;

import chungbuk.cityfarmerplus.application.dto.JobCandidateResponse;
import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;
import chungbuk.cityfarmerplus.jobposting.entity.JobPostingDetails;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JobApplicationTest {

    @Test
    void legacyApplicationKeepsNewCandidateSnapshotsNullable() {
        JobApplication application = JobApplication.apply(
                openPosting(),
                urbanFarmer(),
                Instant.parse("2026-08-01T00:00:00Z"),
                "CHEONGJU",
                "MONDAY",
                1
        );

        assertThat(application.getPreferredStartDateSnapshot()).isNull();
        assertThat(application.getPreferredEndDateSnapshot()).isNull();
        assertThat(application.getAvailableWorkTypesSnapshot()).isNull();
        assertThat(application.getCanTravelSnapshot()).isNull();

        JobCandidateResponse candidate = JobCandidateResponse.from(application);
        assertThat(candidate.preferredStartDateSnapshot()).isNull();
        assertThat(candidate.preferredEndDateSnapshot()).isNull();
        assertThat(candidate.availableWorkTypesSnapshot()).isNull();
        assertThat(candidate.canTravelSnapshot()).isNull();
    }

    @Test
    void reapplyRefreshesCandidateSnapshotsAndClearsPreviousFarmOpinion() {
        JobApplication application = JobApplication.apply(
                openPosting(),
                urbanFarmer(),
                Instant.parse("2026-08-01T00:00:00Z"),
                "CHEONGJU",
                "MONDAY",
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 31),
                "PLANTING",
                false,
                1
        );
        application.updateFarmOpinion(
                JobApplication.FarmOpinion.PREFERRED,
                "경험자를 선호합니다."
        );
        application.withdraw(Instant.parse("2026-08-02T00:00:00Z"));

        Instant reverifiedAt = Instant.parse("2026-08-03T00:00:00Z");
        application.reapply(
                reverifiedAt,
                "CHUNGJU,JECHEON",
                "TUESDAY,WEDNESDAY",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 10, 15),
                "HARVEST,SORTING",
                true,
                5
        );

        assertThat(application.getStatus())
                .isEqualTo(JobApplication.ApplicationStatus.APPLIED);
        assertThat(application.getWithdrawnAt()).isNull();
        assertThat(application.getEducationVerifiedAt()).isEqualTo(reverifiedAt);
        assertThat(application.getPreferredRegionsSnapshot())
                .isEqualTo("CHUNGJU,JECHEON");
        assertThat(application.getAvailableDaysSnapshot())
                .isEqualTo("TUESDAY,WEDNESDAY");
        assertThat(application.getPreferredStartDateSnapshot())
                .isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(application.getPreferredEndDateSnapshot())
                .isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(application.getAvailableWorkTypesSnapshot())
                .isEqualTo("HARVEST,SORTING");
        assertThat(application.getCanTravelSnapshot()).isTrue();
        assertThat(application.getExperienceCountSnapshot()).isEqualTo(5);
        assertThat(application.getFarmOpinion())
                .isEqualTo(JobApplication.FarmOpinion.NONE);
        assertThat(application.getFarmOpinionNote()).isNull();

        JobCandidateResponse candidate = JobCandidateResponse.from(application);
        assertThat(candidate.preferredStartDateSnapshot())
                .isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(candidate.preferredEndDateSnapshot())
                .isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(candidate.availableWorkTypesSnapshot())
                .isEqualTo("HARVEST,SORTING");
        assertThat(candidate.canTravelSnapshot()).isTrue();
    }

    private JobPosting openPosting() {
        JobPosting posting = JobPosting.createDraft(approvedFarm(), details());
        posting.submitForReview(Instant.parse("2026-07-30T00:00:00Z"));
        posting.approve(Instant.parse("2026-07-31T00:00:00Z"));
        return posting;
    }

    private User urbanFarmer() {
        return User.register(
                "urban_1",
                "encoded",
                "도시농부",
                User.UserType.URBAN_FARMER
        );
    }

    private FarmProfile approvedFarm() {
        User owner = User.register(
                "farm_owner",
                "encoded",
                "농가",
                User.UserType.FARM
        );
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
        return farm;
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
