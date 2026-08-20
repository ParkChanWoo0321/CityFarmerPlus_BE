package chungbuk.cityfarmerplus.education.service;

import chungbuk.cityfarmerplus.auth.service.AccountDataCleaner;
import chungbuk.cityfarmerplus.common.storage.FileDeletionScheduler;
import chungbuk.cityfarmerplus.education.repository.EducationCertificateDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

@Component
@Order(100)
@RequiredArgsConstructor
public class EducationAccountDataCleaner implements AccountDataCleaner {

    private final EducationCertificateDocumentRepository documentRepository;
    private final FileDeletionScheduler fileDeletionScheduler;

    @Override
    public void clean(Long userId) {
        fileDeletionScheduler.deleteAfterCommit(
                documentRepository.findAllBySubmissionCertificationUrbanFarmerId(userId)
                        .stream()
                        .map(document -> document.getStorageKey())
                        .toList()
        );
    }
}
