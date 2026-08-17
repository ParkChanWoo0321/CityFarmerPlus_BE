package chungbuk.cityfarmerplus.jobposting.entity;

import chungbuk.cityfarmerplus.auth.entity.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JobPostingReviewTest {

    @Test
    void activeCenterAdminRecordsNormalizedRejectionReasonAndSnapshots() {
        JobPosting posting = posting();
        User reviewer = User.registerCenterAdmin(
                "center_admin",
                "encoded",
                "담당자"
        );

        JobPostingReview review = JobPostingReview.record(
                posting,
                reviewer,
                JobPostingReview.ReviewAction.REJECTED,
                "  작업 일정 보완이 필요합니다.  "
        );

        assertThat(review.getReviewer()).isSameAs(reviewer);
        assertThat(review.getReason()).isEqualTo("작업 일정 보완이 필요합니다.");
        assertThat(review.getTitleSnapshot()).isEqualTo("감자 수확 도우미 모집");
        assertThat(review.getDescriptionSnapshot()).isEqualTo("감자 수확 작업입니다.");
    }

    @Test
    void rejectsReviewerWhoIsNotAnActiveCenterAdmin() {
        JobPosting posting = posting();
        User farm = User.register(
                "farm_owner",
                "encoded",
                "농가",
                User.UserType.FARM
        );
        User withdrawnAdmin = User.registerCenterAdmin(
                "withdrawn_admin",
                "encoded",
                "탈퇴 담당자"
        );
        withdrawnAdmin.withdraw();

        assertThatThrownBy(() -> JobPostingReview.record(
                posting,
                farm,
                JobPostingReview.ReviewAction.APPROVED,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("활성 상태인 담당자");

        assertThatThrownBy(() -> JobPostingReview.record(
                posting,
                withdrawnAdmin,
                JobPostingReview.ReviewAction.APPROVED,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("활성 상태인 담당자");
    }

    @Test
    void rejectionRequiresReasonAndAllActionsRequireType() {
        JobPosting posting = posting();
        User reviewer = User.registerCenterAdmin(
                "center_admin",
                "encoded",
                "담당자"
        );

        assertThatThrownBy(() -> JobPostingReview.record(
                posting,
                reviewer,
                JobPostingReview.ReviewAction.REJECTED,
                " "
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("반려 사유");

        assertThatThrownBy(() -> JobPostingReview.record(
                posting,
                reviewer,
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("처리 유형");
    }

    private JobPosting posting() {
        JobPosting posting = mock(JobPosting.class);
        when(posting.getTitle()).thenReturn("감자 수확 도우미 모집");
        when(posting.getDescription()).thenReturn("감자 수확 작업입니다.");
        return posting;
    }
}
