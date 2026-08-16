package chungbuk.cityfarmerplus.education.service;

import chungbuk.cityfarmerplus.common.storage.FileDeletionScheduler;
import chungbuk.cityfarmerplus.education.entity.EducationCertificateDocument;
import chungbuk.cityfarmerplus.education.repository.EducationCertificateDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EducationAccountDataCleanerTest {

    @Mock
    private EducationCertificateDocumentRepository documentRepository;

    @Mock
    private FileDeletionScheduler fileDeletionScheduler;

    @Test
    void withdrawalSchedulesPhysicalFilesButRetainsEducationAuditMetadata() {
        EducationCertificateDocument first =
                mock(EducationCertificateDocument.class);
        EducationCertificateDocument second =
                mock(EducationCertificateDocument.class);
        when(first.getStorageKey()).thenReturn("education/key-1");
        when(second.getStorageKey()).thenReturn("education/key-2");
        when(documentRepository
                .findAllBySubmissionCertificationUrbanFarmerId(15L))
                .thenReturn(List.of(first, second));
        EducationAccountDataCleaner cleaner = new EducationAccountDataCleaner(
                documentRepository,
                fileDeletionScheduler
        );

        cleaner.clean(15L);

        verify(fileDeletionScheduler).deleteAfterCommit(List.of(
                "education/key-1",
                "education/key-2"
        ));
        verify(documentRepository, never()).deleteAll();
    }
}
