package chungbuk.cityfarmerplus.urbanfarmer.participation.entity;

import chungbuk.cityfarmerplus.auth.entity.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParticipationApplicationTest {

    @Test
    void draftCanBeSubmittedAndApprovedByCenterAdmin() {
        ParticipationApplication application = ParticipationApplication.createDraft(
                user(User.UserType.URBAN_FARMER),
                2026,
                false,
                "참여 희망"
        );
        Instant submittedAt = Instant.parse("2026-08-01T00:00:00Z");
        Instant reviewedAt = Instant.parse("2026-08-02T00:00:00Z");

        application.submit(submittedAt);
        application.approve(user(User.UserType.CENTER_ADMIN), reviewedAt);

        assertThat(application.getStatus())
                .isEqualTo(ParticipationApplication.ParticipationStatus.APPROVED);
        assertThat(application.getSubmittedAt()).isEqualTo(submittedAt);
        assertThat(application.getReviewedAt()).isEqualTo(reviewedAt);
    }

    @Test
    void rejectedApplicationReturnsToDraftOnlyWhenEdited() {
        ParticipationApplication application = ParticipationApplication.createDraft(
                user(User.UserType.URBAN_FARMER),
                2026,
                false,
                null
        );
        application.submit(Instant.now());
        application.reject(user(User.UserType.CENTER_ADMIN), "서류 보완", Instant.now());

        application.updateDraft(true, "보완 완료");

        assertThat(application.getStatus())
                .isEqualTo(ParticipationApplication.ParticipationStatus.DRAFT);
        assertThat(application.getRejectionReason()).isNull();
        assertThat(application.isAgriculturalBusinessRegistered()).isTrue();
    }

    @Test
    void onlySubmittedApplicationCanBeReviewed() {
        ParticipationApplication application = ParticipationApplication.createDraft(
                user(User.UserType.URBAN_FARMER),
                2026,
                false,
                null
        );

        assertThatThrownBy(() -> application.approve(
                user(User.UserType.CENTER_ADMIN),
                Instant.now()
        )).isInstanceOf(IllegalStateException.class);
    }

    private User user(User.UserType type) {
        if (type == User.UserType.CENTER_ADMIN) {
            return User.registerCenterAdmin("admin_1", "encoded-password", "담당자");
        }
        return User.register("urban_1", "encoded-password", "도시농부", type);
    }
}
