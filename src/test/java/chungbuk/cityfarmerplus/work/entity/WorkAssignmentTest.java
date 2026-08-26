package chungbuk.cityfarmerplus.work.entity;

import chungbuk.cityfarmerplus.application.entity.JobApplication;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkAssignmentTest {

    @Test
    void matchedAssignmentKeepsPostingSuppliesAndPrecautionsForWorkGuide() {
        WorkAssignment assignment = fixture().assignment();

        assertThat(assignment.getSupplies()).isEqualTo(details().supplies());
        assertThat(assignment.getPrecautions()).isEqualTo(details().precautions());
        assertThat(assignment.getRecruitmentCapacity()).isEqualTo(details().capacity());
    }

    @Test
    void absentAttendanceMovesAssignmentAndApplicationToNoShow() {
        Fixture fixture = fixture();
        WorkAssignment assignment = fixture.assignment();

        assignment.recordAttendance(
                WorkAssignment.AttendanceStatus.ABSENT,
                fixture.farmOwner(),
                Instant.parse("2026-08-20T00:00:00Z")
        );

        assertThat(assignment.getAttendanceStatus())
                .isEqualTo(WorkAssignment.AttendanceStatus.ABSENT);
        assertThat(assignment.getStatus())
                .isEqualTo(WorkAssignment.WorkStatus.NO_SHOW);
        assertThat(assignment.getJobApplication().getStatus())
                .isEqualTo(JobApplication.ApplicationStatus.NO_SHOW);
    }

    @Test
    void retryingSamePresentAttendanceKeepsOriginalRecord() {
        Fixture fixture = fixture();
        WorkAssignment assignment = fixture.assignment();
        Instant firstRecordedAt = Instant.parse("2026-08-20T00:00:00Z");

        assignment.recordAttendance(
                WorkAssignment.AttendanceStatus.PRESENT,
                fixture.farmOwner(),
                firstRecordedAt
        );
        assignment.recordAttendance(
                WorkAssignment.AttendanceStatus.PRESENT,
                fixture.farmOwner(),
                Instant.parse("2026-08-20T00:05:00Z")
        );

        assertThat(assignment.getAttendanceStatus())
                .isEqualTo(WorkAssignment.AttendanceStatus.PRESENT);
        assertThat(assignment.getAttendanceRecordedBy()).isSameAs(fixture.farmOwner());
        assertThat(assignment.getAttendanceRecordedAt()).isEqualTo(firstRecordedAt);
        assertThat(assignment.getStatus()).isEqualTo(WorkAssignment.WorkStatus.SCHEDULED);
        assertThat(assignment.getJobApplication().getStatus())
                .isEqualTo(JobApplication.ApplicationStatus.MATCHED);
    }

    @Test
    void retryingSameAbsentAttendanceKeepsOriginalNoShowRecord() {
        Fixture fixture = fixture();
        WorkAssignment assignment = fixture.assignment();
        Instant firstRecordedAt = Instant.parse("2026-08-20T00:00:00Z");

        assignment.recordAttendance(
                WorkAssignment.AttendanceStatus.ABSENT,
                fixture.farmOwner(),
                firstRecordedAt
        );
        assignment.recordAttendance(
                WorkAssignment.AttendanceStatus.ABSENT,
                fixture.farmOwner(),
                Instant.parse("2026-08-20T00:05:00Z")
        );

        assertThat(assignment.getAttendanceStatus())
                .isEqualTo(WorkAssignment.AttendanceStatus.ABSENT);
        assertThat(assignment.getAttendanceRecordedBy()).isSameAs(fixture.farmOwner());
        assertThat(assignment.getAttendanceRecordedAt()).isEqualTo(firstRecordedAt);
        assertThat(assignment.getStatus()).isEqualTo(WorkAssignment.WorkStatus.NO_SHOW);
        assertThat(assignment.getJobApplication().getStatus())
                .isEqualTo(JobApplication.ApplicationStatus.NO_SHOW);
    }

    @Test
    void recordingDifferentAttendanceAfterInitialRecordRequiresCorrection() {
        Fixture fixture = fixture();
        WorkAssignment assignment = fixture.assignment();
        Instant firstRecordedAt = Instant.parse("2026-08-20T00:00:00Z");
        assignment.recordAttendance(
                WorkAssignment.AttendanceStatus.PRESENT,
                fixture.farmOwner(),
                firstRecordedAt
        );

        assertThatThrownBy(() -> assignment.recordAttendance(
                WorkAssignment.AttendanceStatus.ABSENT,
                fixture.farmOwner(),
                Instant.parse("2026-08-20T00:05:00Z")
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("담당자만 정정");
        assertThat(assignment.getAttendanceStatus())
                .isEqualTo(WorkAssignment.AttendanceStatus.PRESENT);
        assertThat(assignment.getAttendanceRecordedAt()).isEqualTo(firstRecordedAt);
        assertThat(assignment.getStatus()).isEqualTo(WorkAssignment.WorkStatus.SCHEDULED);
        assertThat(assignment.getJobApplication().getStatus())
                .isEqualTo(JobApplication.ApplicationStatus.MATCHED);
    }

    @Test
    void presentAttendanceThenCompletionMovesBothStatesToCompleted() {
        Fixture fixture = fixture();
        WorkAssignment assignment = fixture.assignment();
        Instant completedAt = Instant.parse("2026-08-20T07:00:00Z");
        assignment.recordAttendance(
                WorkAssignment.AttendanceStatus.PRESENT,
                fixture.farmOwner(),
                Instant.parse("2026-08-20T00:00:00Z")
        );

        assignment.completeByFarm(completedAt);

        assertThat(assignment.getStatus())
                .isEqualTo(WorkAssignment.WorkStatus.COMPLETED);
        assertThat(assignment.getAttendanceStatus())
                .isEqualTo(WorkAssignment.AttendanceStatus.PRESENT);
        assertThat(assignment.getCompletedAt()).isEqualTo(completedAt);
        assertThat(assignment.getJobApplication().getStatus())
                .isEqualTo(JobApplication.ApplicationStatus.WORK_COMPLETED);
    }

    @Test
    void correctingCompletedAttendanceToAbsentClearsCompletionAndMarksNoShow() {
        Fixture fixture = fixture();
        WorkAssignment assignment = fixture.assignment();
        assignment.recordAttendance(
                WorkAssignment.AttendanceStatus.PRESENT,
                fixture.farmOwner(),
                Instant.parse("2026-08-20T00:00:00Z")
        );
        assignment.completeByFarm(Instant.parse("2026-08-20T07:00:00Z"));

        assignment.correctAttendance(
                WorkAssignment.AttendanceStatus.ABSENT,
                Instant.parse("2026-08-20T08:00:00Z")
        );

        assertThat(assignment.getStatus())
                .isEqualTo(WorkAssignment.WorkStatus.NO_SHOW);
        assertThat(assignment.getAttendanceStatus())
                .isEqualTo(WorkAssignment.AttendanceStatus.ABSENT);
        assertThat(assignment.getCompletedAt()).isNull();
        assertThat(assignment.getJobApplication().getStatus())
                .isEqualTo(JobApplication.ApplicationStatus.NO_SHOW);
    }

    @Test
    void cancelledAssignmentRejectsAttendanceCorrection() {
        Fixture fixture = fixture();
        WorkAssignment assignment = fixture.assignment();
        assignment.cancel();

        assertThatThrownBy(() -> assignment.correctAttendance(
                WorkAssignment.AttendanceStatus.PRESENT,
                Instant.parse("2026-08-20T01:00:00Z")
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("취소된 근무");
        assertThat(assignment.getStatus())
                .isEqualTo(WorkAssignment.WorkStatus.CANCELLED);
        assertThat(assignment.getAttendanceStatus())
                .isEqualTo(WorkAssignment.AttendanceStatus.NOT_RECORDED);
    }

    @Test
    void correctingNoShowToPresentRestoresScheduledAndMatchedStates() {
        Fixture fixture = fixture();
        WorkAssignment assignment = fixture.assignment();
        assignment.recordAttendance(
                WorkAssignment.AttendanceStatus.ABSENT,
                fixture.farmOwner(),
                Instant.parse("2026-08-20T00:00:00Z")
        );

        WorkAssignment.AttendanceStatus previous = assignment.correctAttendance(
                WorkAssignment.AttendanceStatus.PRESENT,
                Instant.parse("2026-08-20T01:00:00Z")
        );

        assertThat(previous).isEqualTo(WorkAssignment.AttendanceStatus.ABSENT);
        assertThat(assignment.getAttendanceStatus())
                .isEqualTo(WorkAssignment.AttendanceStatus.PRESENT);
        assertThat(assignment.getStatus())
                .isEqualTo(WorkAssignment.WorkStatus.SCHEDULED);
        assertThat(assignment.getJobApplication().getStatus())
                .isEqualTo(JobApplication.ApplicationStatus.MATCHED);
    }

    private Fixture fixture() {
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
        ReflectionTestUtils.setField(posting, "id", 10L);
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
        application.match(
                User.registerCenterAdmin("admin_1", "encoded", "담당자"),
                Instant.parse("2026-08-02T00:00:00Z")
        );
        return new Fixture(
                WorkAssignment.fromMatchedApplication(application),
                farmOwner
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

    private record Fixture(
            WorkAssignment assignment,
            User farmOwner
    ) {
    }
}
