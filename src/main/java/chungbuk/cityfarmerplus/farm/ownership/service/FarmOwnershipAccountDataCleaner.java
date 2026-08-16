package chungbuk.cityfarmerplus.farm.ownership.service;

import chungbuk.cityfarmerplus.auth.service.AccountDataCleaner;
import chungbuk.cityfarmerplus.common.storage.FileDeletionScheduler;
import chungbuk.cityfarmerplus.farm.ownership.repository.FarmOwnershipDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

@Component
@Order(100)
@RequiredArgsConstructor
public class FarmOwnershipAccountDataCleaner implements AccountDataCleaner {

    private final FarmOwnershipDocumentRepository documentRepository;
    private final FileDeletionScheduler fileDeletionScheduler;

    @Override
    public void clean(Long userId) {
        fileDeletionScheduler.deleteAfterCommit(
                documentRepository.findAllBySubmissionFarmProfileOwnerId(userId)
                        .stream()
                        .map(document -> document.getStorageKey())
                        .toList()
        );
    }
}
