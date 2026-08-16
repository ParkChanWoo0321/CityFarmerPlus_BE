package chungbuk.cityfarmerplus.farm.ownership.service;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import chungbuk.cityfarmerplus.common.storage.FileStorage;
import chungbuk.cityfarmerplus.common.storage.FileDeletionScheduler;
import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import chungbuk.cityfarmerplus.farm.ownership.dto.FarmOwnershipSubmissionResponse;
import chungbuk.cityfarmerplus.farm.ownership.entity.FarmOwnershipSubmission;
import chungbuk.cityfarmerplus.farm.ownership.exception.FarmOwnershipException;
import chungbuk.cityfarmerplus.farm.repository.FarmProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FarmOwnershipSubmissionServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FarmProfileRepository farmProfileRepository;

    @Mock
    private OwnershipDocumentValidator documentValidator;

    @Mock
    private FileStorage fileStorage;

    @Mock
    private FileDeletionScheduler fileDeletionScheduler;

    @Mock
    private FarmOwnershipSubmissionTransactionService transactionService;

    private FarmOwnershipSubmissionService submissionService;

    @BeforeEach
    void setUp() {
        submissionService = new FarmOwnershipSubmissionService(
                userRepository,
                farmProfileRepository,
                documentValidator,
                fileStorage,
                fileDeletionScheduler,
                transactionService
        );
    }

    @Test
    void storesValidatedDocumentsThenPersistsMetadata() {
        prepareActiveDraftProfile();
        MockMultipartFile source = source("land.pdf");
        when(documentValidator.validate(List.of(source)))
                .thenReturn(List.of(validated(source, "land.pdf")));
        when(fileStorage.store(any(), eq(source), eq("pdf"), eq(source.getSize())))
                .thenReturn(storedFile("farm-ownership/100/batch/key.pdf"));
        FarmOwnershipSubmissionResponse expected = response();
        when(transactionService.persist(eq(1L), any())).thenReturn(expected);

        FarmOwnershipSubmissionResponse actual = submissionService.submit(
                1L,
                List.of(source)
        );

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<String> directoryCaptor = ArgumentCaptor.forClass(String.class);
        verify(fileStorage).store(
                directoryCaptor.capture(),
                eq(source),
                eq("pdf"),
                eq(source.getSize())
        );
        assertThat(directoryCaptor.getValue())
                .matches("farm-ownership/100/[0-9a-f-]{36}");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StoredOwnershipDocument>> documentsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(transactionService).persist(eq(1L), documentsCaptor.capture());
        assertThat(documentsCaptor.getValue()).singleElement().satisfies(document -> {
            assertThat(document.originalFilename()).isEqualTo("land.pdf");
            assertThat(document.storageKey())
                    .isEqualTo("farm-ownership/100/batch/key.pdf");
            assertThat(document.sha256()).isEqualTo("a".repeat(64));
        });
        verify(fileStorage, never()).delete(any());
    }

    @Test
    void deletesAlreadyStoredFilesWhenLaterStorageFails() {
        prepareActiveDraftProfile();
        MockMultipartFile first = source("first.pdf");
        MockMultipartFile second = source("second.pdf");
        when(documentValidator.validate(List.of(first, second)))
                .thenReturn(List.of(
                        validated(first, "first.pdf"),
                        validated(second, "second.pdf")
                ));
        when(fileStorage.store(any(), eq(first), eq("pdf"), eq(first.getSize())))
                .thenReturn(storedFile("key-1.pdf"));
        when(fileStorage.store(any(), eq(second), eq("pdf"), eq(second.getSize())))
                .thenThrow(new IllegalStateException("disk full"));

        assertThatThrownBy(() -> submissionService.submit(
                1L,
                List.of(first, second)
        )).isInstanceOf(FarmOwnershipException.class)
                .extracting("code")
                .isEqualTo("OWNERSHIP_DOCUMENT_STORAGE_ERROR");

        verify(fileStorage).delete("key-1.pdf");
        verifyNoInteractions(transactionService);
    }

    @Test
    void deletesAllStoredFilesWhenDatabaseTransactionFails() {
        prepareActiveDraftProfile();
        MockMultipartFile first = source("land.pdf");
        MockMultipartFile second = source("register.pdf");
        when(documentValidator.validate(List.of(first, second)))
                .thenReturn(List.of(
                        validated(first, "land.pdf"),
                        validated(second, "register.pdf")
                ));
        when(fileStorage.store(any(), eq(first), eq("pdf"), eq(first.getSize())))
                .thenReturn(storedFile("key-1.pdf"));
        when(fileStorage.store(any(), eq(second), eq("pdf"), eq(second.getSize())))
                .thenReturn(storedFile("key-2.pdf"));
        FarmOwnershipException conflict = FarmOwnershipException.submissionNotAllowed();
        when(transactionService.persist(eq(1L), any())).thenThrow(conflict);

        assertThatThrownBy(() -> submissionService.submit(
                1L,
                List.of(first, second)
        ))
                .isSameAs(conflict);

        InOrder deletionOrder = inOrder(fileStorage);
        deletionOrder.verify(fileStorage).delete("key-2.pdf");
        deletionOrder.verify(fileStorage).delete("key-1.pdf");
    }

    @Test
    void cleanupFailureDoesNotHideTheDatabaseFailure() {
        prepareActiveDraftProfile();
        MockMultipartFile first = source("land.pdf");
        MockMultipartFile second = source("register.pdf");
        when(documentValidator.validate(List.of(first, second)))
                .thenReturn(List.of(
                        validated(first, "land.pdf"),
                        validated(second, "register.pdf")
                ));
        when(fileStorage.store(any(), eq(first), eq("pdf"), eq(first.getSize())))
                .thenReturn(storedFile("key-1.pdf"));
        when(fileStorage.store(any(), eq(second), eq("pdf"), eq(second.getSize())))
                .thenReturn(storedFile("key-2.pdf"));
        FarmOwnershipException conflict = FarmOwnershipException.dataConflict();
        when(transactionService.persist(eq(1L), any())).thenThrow(conflict);
        doThrow(new IllegalStateException("delete failed"))
                .when(fileStorage).delete("key-2.pdf");

        assertThatThrownBy(() -> submissionService.submit(
                1L,
                List.of(first, second)
        ))
                .isSameAs(conflict);

        verify(fileStorage).delete("key-2.pdf");
        verify(fileStorage).delete("key-1.pdf");
    }

    @Test
    void changedContentBetweenValidationAndStorageIsRejectedAndDeleted() {
        prepareActiveDraftProfile();
        MockMultipartFile source = source("land.pdf");
        when(documentValidator.validate(List.of(source)))
                .thenReturn(List.of(validated(source, "land.pdf")));
        when(fileStorage.store(any(), eq(source), eq("pdf"), eq(source.getSize())))
                .thenReturn(new FileStorage.StoredFile(
                        "key-1.pdf",
                        source.getSize(),
                        "b".repeat(64)
                ));

        assertThatThrownBy(() -> submissionService.submit(1L, List.of(source)))
                .isInstanceOf(FarmOwnershipException.class)
                .extracting("code")
                .isEqualTo("OWNERSHIP_DOCUMENT_STORAGE_ERROR");

        verify(fileStorage).delete("key-1.pdf");
        verifyNoInteractions(transactionService);
    }

    @Test
    void validationFailureNeverWritesAFileOrStartsTheTransaction() {
        prepareActiveDraftProfile();
        MockMultipartFile source = source("land.pdf");
        when(documentValidator.validate(List.of(source)))
                .thenThrow(FarmOwnershipException.invalidDocumentContent());

        assertThatThrownBy(() -> submissionService.submit(1L, List.of(source)))
                .isInstanceOf(FarmOwnershipException.class)
                .extracting("code")
                .isEqualTo("INVALID_OWNERSHIP_DOCUMENT_CONTENT");

        verifyNoInteractions(fileStorage, transactionService);
    }

    @Test
    void rejectsInvalidStateBeforeValidatingOrStoringFiles() {
        User owner = activeFarmUser();
        FarmProfile profile = profile(owner);
        ReflectionTestUtils.setField(
                profile,
                "status",
                FarmProfile.FarmProfileStatus.PENDING_REVIEW
        );
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(farmProfileRepository.findByOwnerId(1L)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> submissionService.submit(
                1L,
                List.of(source("land.pdf"))
        )).isInstanceOf(FarmOwnershipException.class)
                .extracting("code")
                .isEqualTo("OWNERSHIP_SUBMISSION_NOT_ALLOWED");

        verifyNoInteractions(documentValidator, fileStorage, transactionService);
    }

    private void prepareActiveDraftProfile() {
        User owner = activeFarmUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(farmProfileRepository.findByOwnerId(1L))
                .thenReturn(Optional.of(profile(owner)));
    }

    private User activeFarmUser() {
        User user = User.register(
                "farm_1",
                "encoded-password",
                "농가 1",
                User.UserType.FARM
        );
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    private FarmProfile profile(User owner) {
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
        return profile;
    }

    private MockMultipartFile source(String filename) {
        return new MockMultipartFile(
                "documents",
                filename,
                "application/pdf",
                "%PDF-1.7".getBytes()
        );
    }

    private OwnershipDocumentValidator.ValidatedDocument validated(
            MockMultipartFile source,
            String filename
    ) {
        return new OwnershipDocumentValidator.ValidatedDocument(
                source,
                filename,
                "pdf",
                "application/pdf",
                source.getSize(),
                "a".repeat(64)
        );
    }

    private FarmOwnershipSubmissionResponse response() {
        return new FarmOwnershipSubmissionResponse(
                200L,
                1,
                FarmOwnershipSubmission.SubmissionStatus.PENDING_REVIEW,
                FarmProfile.FarmProfileStatus.PENDING_REVIEW,
                Instant.parse("2026-08-04T00:00:00Z"),
                List.of()
        );
    }

    private FileStorage.StoredFile storedFile(String storageKey) {
        return new FileStorage.StoredFile(
                storageKey,
                source("hash-source.pdf").getSize(),
                "a".repeat(64)
        );
    }
}
