package chungbuk.cityfarmerplus.urbanfarmer.participation.service;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.urbanfarmer.participation.entity.ParticipationApplication;
import chungbuk.cityfarmerplus.urbanfarmer.participation.repository.ParticipationApplicationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParticipationAccountDataCleanerTest {

    private static final Long USER_ID = 15L;

    @Mock
    private ParticipationApplicationRepository applicationRepository;

    @Test
    void withdrawalCancelsOnlyNonApprovedApplicationsAndKeepsReviewHistory() {
        User urbanFarmer = User.register(
                "urban_15",
                "encoded-password",
                "도시농부",
                User.UserType.URBAN_FARMER
        );
        User reviewer = User.registerCenterAdmin(
                "center_admin",
                "encoded-password",
                "담당자"
        );

        ParticipationApplication draft = application(urbanFarmer, 2025);
        ParticipationApplication submitted = application(urbanFarmer, 2026);
        submitted.submit(Instant.parse("2026-08-01T00:00:00Z"));

        ParticipationApplication rejected = application(urbanFarmer, 2027);
        rejected.submit(Instant.parse("2026-08-02T00:00:00Z"));
        Instant rejectedAt = Instant.parse("2026-08-03T00:00:00Z");
        rejected.reject(reviewer, "서류 보완", rejectedAt);

        ParticipationApplication approved = application(urbanFarmer, 2028);
        approved.submit(Instant.parse("2026-08-04T00:00:00Z"));
        approved.approve(reviewer, Instant.parse("2026-08-05T00:00:00Z"));

        ParticipationApplication alreadyCancelled = application(urbanFarmer, 2029);
        Instant originalCancelledAt = Instant.parse("2026-08-06T00:00:00Z");
        alreadyCancelled.cancel(originalCancelledAt);

        when(applicationRepository.findAllByUrbanFarmerIdForUpdate(USER_ID))
                .thenReturn(List.of(
                        draft,
                        submitted,
                        rejected,
                        approved,
                        alreadyCancelled
                ));

        new ParticipationAccountDataCleaner(applicationRepository).clean(USER_ID);

        assertThat(List.of(draft, submitted, rejected))
                .allSatisfy(application -> {
                    assertThat(application.getStatus()).isEqualTo(
                            ParticipationApplication.ParticipationStatus.CANCELLED
                    );
                    assertThat(application.getCancelledAt()).isNotNull();
                });
        assertThat(rejected.getReviewedBy()).isSameAs(reviewer);
        assertThat(rejected.getReviewedAt()).isEqualTo(rejectedAt);
        assertThat(rejected.getRejectionReason()).isEqualTo("서류 보완");

        assertThat(approved.getStatus()).isEqualTo(
                ParticipationApplication.ParticipationStatus.APPROVED
        );
        assertThat(approved.getCancelledAt()).isNull();
        assertThat(alreadyCancelled.getCancelledAt()).isEqualTo(originalCancelledAt);
        verify(applicationRepository).findAllByUrbanFarmerIdForUpdate(USER_ID);
    }

    private ParticipationApplication application(User urbanFarmer, int year) {
        return ParticipationApplication.createDraft(
                urbanFarmer,
                year,
                false,
                null
        );
    }
}
