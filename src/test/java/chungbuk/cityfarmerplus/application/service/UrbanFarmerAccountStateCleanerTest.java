package chungbuk.cityfarmerplus.application.service;

import chungbuk.cityfarmerplus.application.entity.JobApplication;
import chungbuk.cityfarmerplus.application.repository.JobApplicationRepository;
import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.common.exception.DomainException;
import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrbanFarmerAccountStateCleanerTest {

    private static final Long USER_ID = 15L;

    @Mock
    private JobApplicationRepository applicationRepository;

    private UrbanFarmerAccountStateCleaner cleaner;

    @BeforeEach
    void setUp() {
        cleaner = new UrbanFarmerAccountStateCleaner(applicationRepository);
    }

    @Test
    void matchedApplicationBlocksWithdrawalBeforeAnyApplicationIsChanged() {
        JobApplication applied = application();
        JobApplication matched = application();
        matched.match(centerAdmin(), Instant.parse("2026-08-10T00:00:00Z"));
        when(applicationRepository.findAllByUrbanFarmerIdForUpdate(USER_ID))
                .thenReturn(List.of(applied, matched));

        assertThatThrownBy(() -> cleaner.clean(USER_ID))
                .isInstanceOfSatisfying(DomainException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("UPCOMING_WORK_EXISTS");
                });

        assertThat(applied.getStatus())
                .isEqualTo(JobApplication.ApplicationStatus.APPLIED);
        assertThat(matched.getStatus())
                .isEqualTo(JobApplication.ApplicationStatus.MATCHED);
        verify(applicationRepository).findAllByUrbanFarmerIdForUpdate(USER_ID);
    }

    @Test
    void withdrawalChangesOnlyAppliedApplicationsAndPreservesTerminalStates() {
        JobApplication applied = application();

        JobApplication withdrawn = application();
        Instant originalWithdrawnAt = Instant.parse("2026-08-09T00:00:00Z");
        withdrawn.withdraw(originalWithdrawnAt);

        JobApplication notMatched = application();
        notMatched.markNotMatched();

        JobApplication postingCancelled = application();
        postingCancelled.cancelWithPosting();

        JobApplication noShow = application();
        noShow.match(centerAdmin(), Instant.parse("2026-08-10T00:00:00Z"));
        noShow.markNoShow();

        JobApplication workCompleted = application();
        workCompleted.match(centerAdmin(), Instant.parse("2026-08-11T00:00:00Z"));
        workCompleted.completeWork();

        when(applicationRepository.findAllByUrbanFarmerIdForUpdate(USER_ID))
                .thenReturn(List.of(
                        applied,
                        withdrawn,
                        notMatched,
                        postingCancelled,
                        noShow,
                        workCompleted
                ));

        cleaner.clean(USER_ID);

        assertThat(applied.getStatus())
                .isEqualTo(JobApplication.ApplicationStatus.WITHDRAWN);
        assertThat(applied.getWithdrawnAt()).isNotNull();
        assertThat(withdrawn.getStatus())
                .isEqualTo(JobApplication.ApplicationStatus.WITHDRAWN);
        assertThat(withdrawn.getWithdrawnAt()).isEqualTo(originalWithdrawnAt);
        assertThat(notMatched.getStatus())
                .isEqualTo(JobApplication.ApplicationStatus.NOT_MATCHED);
        assertThat(postingCancelled.getStatus())
                .isEqualTo(JobApplication.ApplicationStatus.POSTING_CANCELLED);
        assertThat(noShow.getStatus())
                .isEqualTo(JobApplication.ApplicationStatus.NO_SHOW);
        assertThat(workCompleted.getStatus())
                .isEqualTo(JobApplication.ApplicationStatus.WORK_COMPLETED);
        verify(applicationRepository).findAllByUrbanFarmerIdForUpdate(USER_ID);
    }

    private JobApplication application() {
        JobPosting posting = mock(JobPosting.class);
        when(posting.isVisibleToUrbanFarmers()).thenReturn(true);
        return JobApplication.apply(
                posting,
                urbanFarmer(),
                Instant.parse("2026-08-08T00:00:00Z"),
                null,
                null,
                0
        );
    }

    private User urbanFarmer() {
        return User.register(
                "urban-farmer",
                "encoded-password",
                "도시농부",
                User.UserType.URBAN_FARMER
        );
    }

    private User centerAdmin() {
        return User.registerCenterAdmin(
                "center-admin",
                "encoded-password",
                "담당자"
        );
    }
}
