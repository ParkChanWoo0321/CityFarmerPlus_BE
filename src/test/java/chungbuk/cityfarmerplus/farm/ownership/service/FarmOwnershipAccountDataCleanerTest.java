package chungbuk.cityfarmerplus.farm.ownership.service;

import chungbuk.cityfarmerplus.common.storage.FileDeletionScheduler;
import chungbuk.cityfarmerplus.farm.ownership.entity.FarmOwnershipDocument;
import chungbuk.cityfarmerplus.farm.ownership.repository.FarmOwnershipDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FarmOwnershipAccountDataCleanerTest {

    private static final Long USER_ID = 21L;

    @Mock
    private FarmOwnershipDocumentRepository documentRepository;

    @Mock
    private FileDeletionScheduler fileDeletionScheduler;

    @Mock
    private FarmOwnershipDocument firstDocument;

    @Mock
    private FarmOwnershipDocument secondDocument;

    private FarmOwnershipAccountDataCleaner cleaner;

    @BeforeEach
    void setUp() {
        cleaner = new FarmOwnershipAccountDataCleaner(
                documentRepository,
                fileDeletionScheduler
        );
    }

    @Test
    void cleanSchedulesOwnedDocumentStorageKeysForDeletionAfterCommit() {
        when(documentRepository.findAllBySubmissionFarmProfileOwnerId(USER_ID))
                .thenReturn(List.of(firstDocument, secondDocument));
        when(firstDocument.getStorageKey())
                .thenReturn("farm/ownership/user-21/first.pdf");
        when(secondDocument.getStorageKey())
                .thenReturn("farm/ownership/user-21/second.jpg");

        cleaner.clean(USER_ID);

        verify(documentRepository).findAllBySubmissionFarmProfileOwnerId(USER_ID);
        verify(fileDeletionScheduler).deleteAfterCommit(List.of(
                "farm/ownership/user-21/first.pdf",
                "farm/ownership/user-21/second.jpg"
        ));
    }

    @Test
    void cleanSchedulesAnEmptyDeletionWhenTheUserHasNoDocuments() {
        when(documentRepository.findAllBySubmissionFarmProfileOwnerId(USER_ID))
                .thenReturn(List.of());

        cleaner.clean(USER_ID);

        verify(documentRepository).findAllBySubmissionFarmProfileOwnerId(USER_ID);
        verify(fileDeletionScheduler).deleteAfterCommit(List.of());
    }
}
