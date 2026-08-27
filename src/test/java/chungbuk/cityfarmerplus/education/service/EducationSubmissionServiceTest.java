package chungbuk.cityfarmerplus.education.service;

import chungbuk.cityfarmerplus.common.exception.DomainException;
import chungbuk.cityfarmerplus.common.storage.FileDeletionScheduler;
import chungbuk.cityfarmerplus.common.storage.FileStorage;
import chungbuk.cityfarmerplus.common.storage.FileStorageException;
import chungbuk.cityfarmerplus.education.dto.EducationSubmissionRequest;
import chungbuk.cityfarmerplus.education.entity.EducationCertificateSubmission;
import chungbuk.cityfarmerplus.education.entity.EducationCertification;
import chungbuk.cityfarmerplus.education.entity.EducationCourse;
import chungbuk.cityfarmerplus.education.repository.EducationCertificateSubmissionRepository;
import chungbuk.cityfarmerplus.education.repository.EducationCertificationRepository;
import chungbuk.cityfarmerplus.education.repository.EducationCourseRepository;
import chungbuk.cityfarmerplus.urbanfarmer.service.UserRoleAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EducationSubmissionServiceTest {

    private static final Long USER_ID = 15L;
    private static final Long COURSE_ID = 7L;

    @Mock
    private EducationCertificationRepository certificationRepository;

    @Mock
    private EducationCertificateSubmissionRepository submissionRepository;

    @Mock
    private EducationCourseRepository courseRepository;

    @Mock
    private UserRoleAccessService accessService;

    @Mock
    private EducationDocumentValidator documentValidator;

    @Mock
    private FileStorage fileStorage;

    @Mock
    private FileDeletionScheduler fileDeletionScheduler;

    private final EducationSubmissionPolicy submissionPolicy =
            new EducationSubmissionPolicy();

    @Mock
    private EducationSubmissionTransactionService transactionService;

    @Mock
    private EducationProgressService progressService;

    @Test
    void successfulSubmissionRegistersStoredFilesForRollbackCleanup() {
        MultipartFile source = document("certificate.pdf");
        stubSubmission(List.of(validated(source, "hash-1")));
        when(fileStorage.store(
                anyString(),
                eq(source),
                eq("pdf"),
                eq(EducationDocumentValidator.MAX_DOCUMENT_SIZE)
        )).thenReturn(new FileStorage.StoredFile("key-1", 16L, "hash-1"));

        service().submit(USER_ID, request(), List.of(source));

        InOrder persistenceThenRollbackRegistration = inOrder(
                transactionService,
                fileDeletionScheduler
        );
        persistenceThenRollbackRegistration.verify(transactionService).persist(
                eq(USER_ID),
                any(EducationSubmissionRequest.class),
                anyList()
        );
        persistenceThenRollbackRegistration.verify(fileDeletionScheduler)
                .deleteOnRollback(List.of("key-1"));
        verify(fileDeletionScheduler, never()).deleteNowWithRetry(anyList(), anyString());
        verify(fileStorage, never()).delete(anyString());
    }

    @Test
    void metadataMismatchDeletesTheFileThatWasJustStored() {
        MultipartFile source = document("certificate.pdf");
        stubSubmission(List.of(validated(source, "expected-hash")));
        when(fileStorage.store(
                anyString(),
                eq(source),
                eq("pdf"),
                anyLong()
        )).thenReturn(new FileStorage.StoredFile(
                "mismatched-key",
                16L,
                "different-hash"
        ));

        assertThatThrownBy(() -> service().submit(
                USER_ID,
                request(),
                List.of(source)
        )).isInstanceOf(DomainException.class)
                .extracting("code")
                .isEqualTo("EDUCATION_DOCUMENT_STORAGE_FAILED");

        verify(fileDeletionScheduler).deleteNowWithRetry(
                List.of("mismatched-key"),
                "EDUCATION_UPLOAD_COMPENSATION"
        );
        verify(transactionService, never()).persist(
                anyLong(),
                any(EducationSubmissionRequest.class),
                anyList()
        );
    }

    @Test
    void partialStorageFailureDeletesPreviouslyStoredFiles() {
        MultipartFile first = document("first.pdf");
        MultipartFile second = document("second.pdf");
        stubSubmission(List.of(
                validated(first, "hash-1"),
                validated(second, "hash-2")
        ));
        when(fileStorage.store(anyString(), any(), eq("pdf"), anyLong()))
                .thenReturn(new FileStorage.StoredFile("key-1", 16L, "hash-1"))
                .thenThrow(new FileStorageException("disk full"));

        assertThatThrownBy(() -> service().submit(
                USER_ID,
                request(),
                List.of(first, second)
        )).isInstanceOf(DomainException.class)
                .extracting("code")
                .isEqualTo("EDUCATION_DOCUMENT_STORAGE_FAILED");

        verify(fileDeletionScheduler).deleteNowWithRetry(
                List.of("key-1"),
                "EDUCATION_UPLOAD_COMPENSATION"
        );
        verify(transactionService, never()).persist(
                anyLong(),
                any(EducationSubmissionRequest.class),
                anyList()
        );
    }

    @Test
    void transactionFailureDeletesEveryStoredFile() {
        MultipartFile source = document("certificate.pdf");
        stubSubmission(List.of(validated(source, "hash-1")));
        when(fileStorage.store(anyString(), eq(source), eq("pdf"), anyLong()))
                .thenReturn(new FileStorage.StoredFile("key-1", 16L, "hash-1"));
        when(transactionService.persist(
                eq(USER_ID),
                any(EducationSubmissionRequest.class),
                anyList()
        ))
                .thenThrow(new IllegalStateException("database failed"));

        assertThatThrownBy(() -> service().submit(
                USER_ID,
                request(),
                List.of(source)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("database failed");

        verify(fileDeletionScheduler).deleteNowWithRetry(
                List.of("key-1"),
                "EDUCATION_UPLOAD_COMPENSATION"
        );
    }

    @Test
    void pendingSubmissionIsRejectedBeforeValidatingOrWritingAnotherFile() {
        EducationCourse course = course();
        ReflectionTestUtils.setField(course, "id", COURSE_ID);
        var user = chungbuk.cityfarmerplus.auth.entity.User.register(
                "urban_15",
                "encoded",
                "도시농부",
                chungbuk.cityfarmerplus.auth.entity.User.UserType.URBAN_FARMER
        );
        EducationCertification certification = EducationCertification.create(user);
        ReflectionTestUtils.setField(certification, "id", 100L);
        EducationCertificateSubmission pending =
                EducationCertificateSubmission.createPending(
                        certification,
                        course,
                        1,
                        LocalDate.of(2026, 8, 1),
                        8
                );
        when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
        when(certificationRepository.findByUrbanFarmerId(USER_ID))
                .thenReturn(Optional.of(certification));
        when(submissionRepository
                .findTopByCertificationIdAndCourseIdOrderByAttemptNumberDesc(
                        100L,
                        COURSE_ID
                )).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service().submit(
                USER_ID,
                request(),
                List.of(document("certificate.pdf"))
        )).isInstanceOf(DomainException.class)
                .extracting("code")
                .isEqualTo("EDUCATION_SUBMISSION_NOT_ALLOWED");

        verify(documentValidator, never()).validate(anyList());
        verify(fileStorage, never()).store(anyString(), any(), anyString(), anyLong());
        verify(transactionService, never()).persist(
                anyLong(),
                any(EducationSubmissionRequest.class),
                anyList()
        );
    }

    private void stubSubmission(
            List<EducationDocumentValidator.ValidatedEducationDocument> validated
    ) {
        when(courseRepository.findById(COURSE_ID))
                .thenReturn(Optional.of(course()));
        when(documentValidator.validate(any())).thenReturn(validated);
    }

    private EducationSubmissionService service() {
        return new EducationSubmissionService(
                certificationRepository,
                submissionRepository,
                courseRepository,
                accessService,
                documentValidator,
                fileStorage,
                fileDeletionScheduler,
                submissionPolicy,
                transactionService,
                progressService
        );
    }

    private EducationSubmissionRequest request() {
        return new EducationSubmissionRequest(
                COURSE_ID,
                LocalDate.of(2026, 8, 1),
                8
        );
    }

    private EducationCourse course() {
        return EducationCourse.create(
                "농업안전 기초",
                "필수 교육",
                8,
                "https://example.com/course",
                true,
                1
        );
    }

    private MockMultipartFile document(String filename) {
        return new MockMultipartFile(
                "documents",
                filename,
                "application/pdf",
                new byte[16]
        );
    }

    private EducationDocumentValidator.ValidatedEducationDocument validated(
            MultipartFile source,
            String sha256
    ) {
        return new EducationDocumentValidator.ValidatedEducationDocument(
                source,
                source.getOriginalFilename(),
                "pdf",
                "application/pdf",
                16L,
                sha256
        );
    }
}
