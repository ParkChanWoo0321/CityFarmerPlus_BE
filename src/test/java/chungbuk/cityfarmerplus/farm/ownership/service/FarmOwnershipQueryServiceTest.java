package chungbuk.cityfarmerplus.farm.ownership.service;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.exception.AuthException;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import chungbuk.cityfarmerplus.common.storage.FileStorage;
import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import chungbuk.cityfarmerplus.farm.ownership.dto.FarmOwnershipDocumentDownload;
import chungbuk.cityfarmerplus.farm.ownership.dto.FarmOwnershipSubmissionResponse;
import chungbuk.cityfarmerplus.farm.ownership.entity.FarmOwnershipDocument;
import chungbuk.cityfarmerplus.farm.ownership.entity.FarmOwnershipSubmission;
import chungbuk.cityfarmerplus.farm.ownership.exception.FarmOwnershipException;
import chungbuk.cityfarmerplus.farm.ownership.repository.FarmOwnershipDocumentRepository;
import chungbuk.cityfarmerplus.farm.ownership.repository.FarmOwnershipSubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FarmOwnershipQueryServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FarmOwnershipSubmissionRepository submissionRepository;

    @Mock
    private FarmOwnershipDocumentRepository documentRepository;

    @Mock
    private FileStorage fileStorage;

    private FarmOwnershipQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new FarmOwnershipQueryService(
                userRepository,
                submissionRepository,
                documentRepository,
                fileStorage
        );
    }

    @Test
    void farmOwnerGetsEverySubmissionAttemptInRepositoryOrder() {
        User owner = farmUser(1L);
        FarmProfile profile = profile(owner);
        FarmOwnershipSubmission submission = submission(profile, 2);
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(submissionRepository.findAllDetailedByOwnerId(1L))
                .thenReturn(List.of(submission));

        List<FarmOwnershipSubmissionResponse> responses = queryService.getMine(1L);

        assertThat(responses).singleElement().satisfies(response -> {
            assertThat(response.attemptNumber()).isEqualTo(2);
            assertThat(response.documents()).hasSize(1);
        });
        verify(submissionRepository).findAllDetailedByOwnerId(1L);
    }

    @Test
    void ownerDownloadsDocumentByDatabaseIdWithoutExposingStorageKey() {
        User owner = farmUser(1L);
        FarmOwnershipDocument document = document(profile(owner));
        ByteArrayResource resource = new ByteArrayResource("proof".getBytes());
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(documentRepository.findById(300L)).thenReturn(Optional.of(document));
        when(fileStorage.load("private/key.pdf")).thenReturn(resource);

        FarmOwnershipDocumentDownload download = queryService.download(1L, 300L);

        assertThat(download.resource()).isSameAs(resource);
        assertThat(download.originalFilename()).isEqualTo("토지대장.pdf");
        verify(fileStorage).load("private/key.pdf");
    }

    @Test
    void anotherFarmCannotDownloadOwnersDocument() {
        User owner = farmUser(1L);
        User anotherFarm = farmUser(2L);
        FarmOwnershipDocument document = document(profile(owner));
        when(userRepository.findById(2L)).thenReturn(Optional.of(anotherFarm));
        when(documentRepository.findById(300L)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> queryService.download(2L, 300L))
                .isInstanceOf(FarmOwnershipException.class)
                .extracting("code")
                .isEqualTo("OWNERSHIP_DOCUMENT_ACCESS_DENIED");
    }

    @Test
    void withdrawnFarmCannotDownloadOwnershipDocumentWhileDeletionIsPending() {
        User owner = farmUser(1L);
        owner.withdraw();
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> queryService.download(1L, 300L))
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo("INACTIVE_ACCOUNT");

        verifyNoInteractions(documentRepository, fileStorage);
    }

    private FarmOwnershipDocument document(FarmProfile profile) {
        return submission(profile, 1).getDocuments().get(0);
    }

    private FarmOwnershipSubmission submission(FarmProfile profile, int attempt) {
        FarmOwnershipSubmission submission = FarmOwnershipSubmission.createPending(
                profile,
                attempt
        );
        submission.addDocument(
                "토지대장.pdf",
                "private/key.pdf",
                "application/pdf",
                5L,
                "a".repeat(64)
        );
        ReflectionTestUtils.setField(submission, "id", 200L);
        ReflectionTestUtils.setField(
                submission,
                "submittedAt",
                Instant.parse("2026-08-09T00:00:00Z")
        );
        ReflectionTestUtils.setField(submission.getDocuments().get(0), "id", 300L);
        return submission;
    }

    private FarmProfile profile(User owner) {
        FarmProfile profile = FarmProfile.createDraft(
                owner,
                "충주 농가",
                "농부",
                "01012345678",
                "충주시 예시로 1",
                ChungbukCityCounty.CHUNGJU,
                List.of("사과"),
                "사과 재배",
                null
        );
        ReflectionTestUtils.setField(profile, "id", 100L);
        return profile;
    }

    private User farmUser(Long id) {
        User user = User.register(
                "farm_" + id,
                "encoded-password",
                "농가 " + id,
                User.UserType.FARM
        );
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

}
