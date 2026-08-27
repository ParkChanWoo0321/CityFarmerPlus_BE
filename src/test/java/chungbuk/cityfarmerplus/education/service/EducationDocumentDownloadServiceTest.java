package chungbuk.cityfarmerplus.education.service;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.common.exception.DomainException;
import chungbuk.cityfarmerplus.common.storage.FileStorage;
import chungbuk.cityfarmerplus.common.storage.FileStorageException;
import chungbuk.cityfarmerplus.education.entity.EducationCertificateDocument;
import chungbuk.cityfarmerplus.education.entity.EducationCertificateSubmission;
import chungbuk.cityfarmerplus.education.entity.EducationCertification;
import chungbuk.cityfarmerplus.education.entity.EducationCourse;
import chungbuk.cityfarmerplus.education.repository.EducationCertificateDocumentRepository;
import chungbuk.cityfarmerplus.urbanfarmer.service.UserRoleAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EducationDocumentDownloadServiceTest {

    private static final Long USER_ID = 15L;
    private static final Long SUBMISSION_ID = 200L;
    private static final Long DOCUMENT_ID = 300L;

    @Mock
    private EducationCertificateDocumentRepository documentRepository;

    @Mock
    private UserRoleAccessService accessService;

    @Mock
    private FileStorage fileStorage;

    @Test
    void ownerDownloadsDocumentThroughCompositeOwnershipLookup() {
        EducationCertificateDocument document = document(urbanFarmer());
        ByteArrayResource resource = new ByteArrayResource("proof".getBytes());
        when(documentRepository
                .findByIdAndSubmissionIdAndSubmissionCertificationUrbanFarmerId(
                        DOCUMENT_ID,
                        SUBMISSION_ID,
                        USER_ID
                ))
                .thenReturn(Optional.of(document));
        when(fileStorage.load("education/key-1")).thenReturn(resource);

        EducationDocumentDownloadService.DownloadedEducationDocument downloaded =
                service().downloadMine(USER_ID, SUBMISSION_ID, DOCUMENT_ID);

        assertThat(downloaded.resource()).isSameAs(resource);
        assertThat(downloaded.originalFilename()).isEqualTo("이수증.pdf");
        assertThat(downloaded.contentType()).isEqualTo("application/pdf");
        assertThat(downloaded.sizeBytes()).isEqualTo(5L);
        verify(accessService).requireUrbanFarmer(USER_ID);
    }

    @Test
    void anotherUsersOrMismatchedSubmissionDocumentIsNotExposed() {
        when(documentRepository
                .findByIdAndSubmissionIdAndSubmissionCertificationUrbanFarmerId(
                        DOCUMENT_ID,
                        SUBMISSION_ID,
                        USER_ID
                ))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().downloadMine(
                USER_ID,
                SUBMISSION_ID,
                DOCUMENT_ID
        )).isInstanceOf(DomainException.class)
                .extracting("code")
                .isEqualTo("EDUCATION_DOCUMENT_NOT_FOUND");

        verify(fileStorage, never()).load("education/key-1");
    }

    @Test
    void withdrawnOwnerCannotDownloadRetainedDocumentMetadata() {
        User withdrawn = urbanFarmer();
        withdrawn.withdraw();
        EducationCertificateDocument document = document(withdrawn);
        when(documentRepository
                .findByIdAndSubmissionIdAndSubmissionCertificationUrbanFarmerId(
                        DOCUMENT_ID,
                        SUBMISSION_ID,
                        USER_ID
                ))
                .thenReturn(Optional.of(document));

        assertThatThrownBy(() -> service().downloadMine(
                USER_ID,
                SUBMISSION_ID,
                DOCUMENT_ID
        )).isInstanceOf(DomainException.class)
                .extracting("code")
                .isEqualTo("EDUCATION_DOCUMENT_FILE_UNAVAILABLE");

        verify(fileStorage, never()).load("education/key-1");
    }

    @Test
    void missingPhysicalFileUsesGoneErrorContract() {
        EducationCertificateDocument document = document(urbanFarmer());
        when(documentRepository
                .findByIdAndSubmissionIdAndSubmissionCertificationUrbanFarmerId(
                        DOCUMENT_ID,
                        SUBMISSION_ID,
                        USER_ID
                ))
                .thenReturn(Optional.of(document));
        when(fileStorage.load("education/key-1"))
                .thenThrow(new FileStorageException("missing"));

        assertThatThrownBy(() -> service().downloadMine(
                USER_ID,
                SUBMISSION_ID,
                DOCUMENT_ID
        )).isInstanceOf(DomainException.class)
                .extracting("code")
                .isEqualTo("EDUCATION_DOCUMENT_FILE_UNAVAILABLE");
    }

    @Test
    void adminDownloadsDocumentForReviewEvenAfterOwnerWithdrawal() {
        User withdrawn = urbanFarmer();
        withdrawn.withdraw();
        EducationCertificateDocument document = document(withdrawn);
        ByteArrayResource resource = new ByteArrayResource("proof".getBytes());
        when(documentRepository.findByIdAndSubmissionId(DOCUMENT_ID, SUBMISSION_ID))
                .thenReturn(Optional.of(document));
        when(fileStorage.load("education/key-1")).thenReturn(resource);

        EducationDocumentDownloadService.DownloadedEducationDocument downloaded =
                service().downloadForAdmin(SUBMISSION_ID, DOCUMENT_ID);

        assertThat(downloaded.resource()).isSameAs(resource);
        assertThat(downloaded.originalFilename()).isEqualTo("이수증.pdf");
        verifyNoInteractions(accessService);
    }

    @Test
    void adminCannotDownloadDocumentFromAnotherSubmission() {
        when(documentRepository.findByIdAndSubmissionId(DOCUMENT_ID, SUBMISSION_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().downloadForAdmin(
                SUBMISSION_ID,
                DOCUMENT_ID
        )).isInstanceOf(DomainException.class)
                .extracting("code")
                .isEqualTo("EDUCATION_DOCUMENT_NOT_FOUND");

        verifyNoInteractions(accessService, fileStorage);
    }

    private EducationDocumentDownloadService service() {
        return new EducationDocumentDownloadService(
                documentRepository,
                accessService,
                fileStorage
        );
    }

    private User urbanFarmer() {
        User user = User.register(
                "urban_15",
                "encoded",
                "도시농부",
                User.UserType.URBAN_FARMER
        );
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

    private EducationCertificateDocument document(User urbanFarmer) {
        EducationCertification certification =
                EducationCertification.create(urbanFarmer);
        ReflectionTestUtils.setField(certification, "id", 100L);
        EducationCourse course = EducationCourse.create(
                "농업안전 기초",
                "필수 교육",
                8,
                "https://example.com/course",
                true,
                1
        );
        ReflectionTestUtils.setField(course, "id", 7L);
        EducationCertificateSubmission submission =
                EducationCertificateSubmission.createPending(
                        certification,
                        course,
                        1,
                        LocalDate.of(2026, 8, 1),
                        8
                );
        ReflectionTestUtils.setField(submission, "id", SUBMISSION_ID);
        submission.addDocument(
                "이수증.pdf",
                "education/key-1",
                "application/pdf",
                5L,
                "a".repeat(64)
        );
        EducationCertificateDocument document = submission.getDocuments().get(0);
        ReflectionTestUtils.setField(document, "id", DOCUMENT_ID);
        return document;
    }
}
