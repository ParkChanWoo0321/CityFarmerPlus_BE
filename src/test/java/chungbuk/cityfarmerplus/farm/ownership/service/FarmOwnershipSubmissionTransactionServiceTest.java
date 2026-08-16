package chungbuk.cityfarmerplus.farm.ownership.service;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.exception.AuthException;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import chungbuk.cityfarmerplus.farm.ownership.dto.FarmOwnershipSubmissionResponse;
import chungbuk.cityfarmerplus.farm.ownership.entity.FarmOwnershipSubmission;
import chungbuk.cityfarmerplus.farm.ownership.exception.FarmOwnershipException;
import chungbuk.cityfarmerplus.farm.ownership.repository.FarmOwnershipSubmissionRepository;
import chungbuk.cityfarmerplus.farm.repository.FarmProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FarmOwnershipSubmissionTransactionServiceTest {

    @Mock
    private FarmProfileRepository farmProfileRepository;

    @Mock
    private FarmOwnershipSubmissionRepository submissionRepository;

    @Mock
    private UserRepository userRepository;

    private FarmOwnershipSubmissionTransactionService transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new FarmOwnershipSubmissionTransactionService(
                farmProfileRepository,
                submissionRepository,
                userRepository
        );
    }

    @Test
    void persistsFirstSubmissionAndMovesProfileToPendingReview() {
        FarmProfile profile = profile(
                user(1L, User.AccountStatus.ACTIVE),
                FarmProfile.FarmProfileStatus.DRAFT
        );
        when(userRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(profile.getOwner()));
        when(farmProfileRepository.findByOwnerIdForUpdate(1L))
                .thenReturn(Optional.of(profile));
        when(submissionRepository.findMaxAttemptNumberByFarmProfileId(100L))
                .thenReturn(0);
        when(submissionRepository.saveAndFlush(any(FarmOwnershipSubmission.class)))
                .thenAnswer(invocation -> persisted(invocation.getArgument(0)));

        FarmOwnershipSubmissionResponse response = transactionService.persist(
                1L,
                List.of(storedDocument("key-1"))
        );

        assertThat(response.id()).isEqualTo(200L);
        assertThat(response.attemptNumber()).isEqualTo(1);
        assertThat(response.status())
                .isEqualTo(FarmOwnershipSubmission.SubmissionStatus.PENDING_REVIEW);
        assertThat(response.farmProfileStatus())
                .isEqualTo(FarmProfile.FarmProfileStatus.PENDING_REVIEW);
        assertThat(response.documents()).singleElement().satisfies(document -> {
            assertThat(document.id()).isEqualTo(300L);
            assertThat(document.originalFilename()).isEqualTo("토지대장.pdf");
            assertThat(document.contentType()).isEqualTo("application/pdf");
            assertThat(document.sizeBytes()).isEqualTo(123L);
        });
        assertThat(profile.getStatus())
                .isEqualTo(FarmProfile.FarmProfileStatus.PENDING_REVIEW);
    }

    @Test
    void rejectedProfileCreatesANewAttemptWithoutChangingPastAttempt() {
        FarmProfile profile = profile(
                user(1L, User.AccountStatus.ACTIVE),
                FarmProfile.FarmProfileStatus.REJECTED
        );
        when(userRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(profile.getOwner()));
        when(farmProfileRepository.findByOwnerIdForUpdate(1L))
                .thenReturn(Optional.of(profile));
        when(submissionRepository.findMaxAttemptNumberByFarmProfileId(100L))
                .thenReturn(1);
        when(submissionRepository.saveAndFlush(any(FarmOwnershipSubmission.class)))
                .thenAnswer(invocation -> persisted(invocation.getArgument(0)));

        FarmOwnershipSubmissionResponse response = transactionService.persist(
                1L,
                List.of(storedDocument("key-2"))
        );

        assertThat(response.attemptNumber()).isEqualTo(2);
        assertThat(response.farmProfileStatus())
                .isEqualTo(FarmProfile.FarmProfileStatus.PENDING_REVIEW);
    }

    @ParameterizedTest
    @EnumSource(
            value = FarmProfile.FarmProfileStatus.class,
            names = {"PENDING_REVIEW", "APPROVED", "INACTIVE"}
    )
    void nonSubmittableProfileStatusCannotSubmitAgain(
            FarmProfile.FarmProfileStatus status
    ) {
        FarmProfile profile = profile(
                user(1L, User.AccountStatus.ACTIVE),
                status
        );
        when(userRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(profile.getOwner()));
        when(farmProfileRepository.findByOwnerIdForUpdate(1L))
                .thenReturn(Optional.of(profile));

        assertCode(
                () -> transactionService.persist(
                        1L,
                        List.of(storedDocument("key-1"))
                ),
                "OWNERSHIP_SUBMISSION_NOT_ALLOWED"
        );

        verify(submissionRepository, never())
                .findMaxAttemptNumberByFarmProfileId(any());
        verify(submissionRepository, never()).saveAndFlush(any());
    }

    @Test
    void rechecksDatabaseAccountStatusInsideTransaction() {
        FarmProfile profile = profile(
                user(1L, User.AccountStatus.SUSPENDED),
                FarmProfile.FarmProfileStatus.DRAFT
        );
        when(userRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(profile.getOwner()));

        assertThatThrownBy(() -> transactionService.persist(
                1L,
                List.of(storedDocument("key-1"))
        )).isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo("INACTIVE_ACCOUNT");

        verify(submissionRepository, never()).saveAndFlush(any());
    }

    @Test
    void databaseConstraintConflictUsesOwnershipErrorContract() {
        FarmProfile profile = profile(
                user(1L, User.AccountStatus.ACTIVE),
                FarmProfile.FarmProfileStatus.DRAFT
        );
        when(userRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(profile.getOwner()));
        when(farmProfileRepository.findByOwnerIdForUpdate(1L))
                .thenReturn(Optional.of(profile));
        when(submissionRepository.findMaxAttemptNumberByFarmProfileId(100L))
                .thenReturn(0);
        when(submissionRepository.saveAndFlush(any(FarmOwnershipSubmission.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertCode(
                () -> transactionService.persist(
                        1L,
                        List.of(storedDocument("key-1"))
                ),
                "OWNERSHIP_SUBMISSION_DATA_CONFLICT"
        );
    }

    @Test
    void cannotPersistSubmissionWithoutStoredDocuments() {
        assertCode(
                () -> transactionService.persist(1L, List.of()),
                "OWNERSHIP_DOCUMENTS_REQUIRED"
        );

        verify(farmProfileRepository, never()).findByOwnerIdForUpdate(any());
    }

    private StoredOwnershipDocument storedDocument(String storageKey) {
        return new StoredOwnershipDocument(
                "토지대장.pdf",
                storageKey,
                "application/pdf",
                123L,
                "a".repeat(64)
        );
    }

    private User user(Long id, User.AccountStatus status) {
        User user = User.register(
                "farm_" + id,
                "encoded-password",
                "농가 " + id,
                User.UserType.FARM
        );
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "accountStatus", status);
        return user;
    }

    private FarmProfile profile(
            User owner,
            FarmProfile.FarmProfileStatus status
    ) {
        FarmProfile profile = FarmProfile.createDraft(
                owner,
                "충주 사과농원",
                "홍길동",
                "01012345678",
                "충청북도 충주시 예시로 1",
                ChungbukCityCounty.CHUNGJU,
                List.of("사과"),
                "사과 재배와 수확 작업을 합니다.",
                null
        );
        ReflectionTestUtils.setField(profile, "id", 100L);
        ReflectionTestUtils.setField(profile, "status", status);
        return profile;
    }

    private FarmOwnershipSubmission persisted(FarmOwnershipSubmission submission) {
        Instant now = Instant.parse("2026-08-04T00:00:00Z");
        ReflectionTestUtils.setField(submission, "id", 200L);
        ReflectionTestUtils.setField(submission, "submittedAt", now);
        for (int index = 0; index < submission.getDocuments().size(); index++) {
            ReflectionTestUtils.setField(
                    submission.getDocuments().get(index),
                    "id",
                    300L + index
            );
            ReflectionTestUtils.setField(
                    submission.getDocuments().get(index),
                    "createdAt",
                    now
            );
        }
        return submission;
    }

    private void assertCode(ThrowingCall call, String expectedCode) {
        assertThatThrownBy(call::run)
                .isInstanceOf(FarmOwnershipException.class)
                .extracting("code")
                .isEqualTo(expectedCode);
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }
}
