package chungbuk.cityfarmerplus.farm.ownership.entity;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FarmOwnershipSubmissionTest {

    @Test
    void pendingSubmissionPreservesFarmIdentityAndDocumentOrder() {
        FarmProfile profile = farmProfile();

        FarmOwnershipSubmission submission =
                FarmOwnershipSubmission.createPending(profile, 2);
        submission.addDocument(
                "토지대장.pdf",
                "ownership/farm-1/attempt-2/land.pdf",
                "application/pdf",
                1024L,
                "a".repeat(64)
        );
        submission.addDocument(
                "농지원부.png",
                "ownership/farm-1/attempt-2/register.png",
                "image/png",
                2048L,
                "b".repeat(64)
        );

        assertThat(submission.getAttemptNumber()).isEqualTo(2);
        assertThat(submission.getStatus())
                .isEqualTo(FarmOwnershipSubmission.SubmissionStatus.PENDING_REVIEW);
        assertThat(submission.getFarmNameSnapshot()).isEqualTo("충주 사과 농장");
        assertThat(submission.getRepresentativeNameSnapshot()).isEqualTo("홍길동");
        assertThat(submission.getFarmAddressSnapshot()).isEqualTo("충주시 예시로 1");
        assertThat(submission.getCityCountySnapshot())
                .isEqualTo(ChungbukCityCounty.CHUNGJU);
        assertThat(submission.getBusinessRegistrationNumberSnapshot())
                .isEqualTo("1234567890");
        assertThat(submission.getFarmAreaPyeongSnapshot()).isEqualTo(350);
        assertThat(submission.getDocuments())
                .extracting(FarmOwnershipDocument::getOriginalFilename)
                .containsExactly("토지대장.pdf", "농지원부.png");
    }

    @Test
    void centerAdminCanRejectPendingSubmissionWithReason() {
        FarmOwnershipSubmission submission =
                FarmOwnershipSubmission.createPending(farmProfile(), 1);
        User reviewer = User.registerCenterAdmin(
                "center_admin",
                "encoded-password",
                "담당자"
        );
        Instant reviewedAt = Instant.parse("2026-08-11T01:00:00Z");

        submission.reject(reviewer, reviewedAt, "소유자 정보가 일치하지 않습니다.");

        assertThat(submission.getStatus())
                .isEqualTo(FarmOwnershipSubmission.SubmissionStatus.REJECTED);
        assertThat(submission.getReviewer()).isSameAs(reviewer);
        assertThat(submission.getReviewedAt()).isEqualTo(reviewedAt);
        assertThat(submission.getRejectionReason())
                .isEqualTo("소유자 정보가 일치하지 않습니다.");
    }

    @Test
    void farmAccountCannotReviewOwnershipSubmission() {
        FarmOwnershipSubmission submission =
                FarmOwnershipSubmission.createPending(farmProfile(), 1);
        User farmReviewer = User.register(
                "another_farm",
                "encoded-password",
                "다른 농가",
                User.UserType.FARM
        );

        assertThatThrownBy(() -> submission.approve(
                farmReviewer,
                Instant.parse("2026-08-11T01:00:00Z")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("담당자만 소유 증빙을 심사할 수 있습니다.");
    }

    private FarmProfile farmProfile() {
        User owner = User.register(
                "farm_owner",
                "encoded-password",
                "농가 사용자",
                User.UserType.FARM
        );
        return FarmProfile.createDraft(
                owner,
                "충주 사과 농장",
                "홍길동",
                "01012345678",
                "충주시 예시로 1",
                ChungbukCityCounty.CHUNGJU,
                List.of("사과"),
                "사과 재배",
                "1234567890",
                350
        );
    }
}
