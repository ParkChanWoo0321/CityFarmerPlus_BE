package chungbuk.cityfarmerplus.farm.ownership.service;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.exception.AuthException;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import chungbuk.cityfarmerplus.common.storage.FileStorage;
import chungbuk.cityfarmerplus.common.storage.FileDeletionScheduler;
import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import chungbuk.cityfarmerplus.farm.exception.FarmProfileException;
import chungbuk.cityfarmerplus.farm.ownership.dto.FarmOwnershipSubmissionResponse;
import chungbuk.cityfarmerplus.farm.ownership.exception.FarmOwnershipException;
import chungbuk.cityfarmerplus.farm.repository.FarmProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FarmOwnershipSubmissionService {

    private final UserRepository userRepository;
    private final FarmProfileRepository farmProfileRepository;
    private final OwnershipDocumentValidator documentValidator;
    private final FileStorage fileStorage;
    private final FileDeletionScheduler fileDeletionScheduler;
    private final FarmOwnershipSubmissionTransactionService transactionService;

    public FarmOwnershipSubmissionResponse submit(
            Long userId,
            List<MultipartFile> documents
    ) {
        Long farmProfileId = getActiveFarmProfileId(userId);
        List<OwnershipDocumentValidator.ValidatedDocument> validatedDocuments =
                documentValidator.validate(documents);
        List<StoredOwnershipDocument> storedDocuments = storeDocuments(
                farmProfileId,
                validatedDocuments
        );

        try {
            FarmOwnershipSubmissionResponse response = transactionService.persist(
                    userId, storedDocuments);
            fileDeletionScheduler.deleteOnRollback(
                    storedDocuments.stream()
                            .map(StoredOwnershipDocument::storageKey)
                            .toList()
            );
            return response;
        } catch (RuntimeException exception) {
            deleteQuietly(storedDocuments);
            throw exception;
        }
    }

    private Long getActiveFarmProfileId(Long userId) {
        User owner = userRepository.findById(userId)
                .orElseThrow(AuthException::userNotFound);
        if (!owner.isActive()) {
            throw AuthException.inactiveAccount();
        }
        if (owner.getUserType() != User.UserType.FARM) {
            throw FarmProfileException.farmRoleRequired();
        }

        FarmProfile profile = farmProfileRepository.findByOwnerId(userId)
                .orElseThrow(FarmProfileException::profileNotFound);
        if (!profile.canSubmitOwnershipDocuments()) {
            throw FarmOwnershipException.submissionNotAllowed();
        }
        return profile.getId();
    }

    private List<StoredOwnershipDocument> storeDocuments(
            Long farmProfileId,
            List<OwnershipDocumentValidator.ValidatedDocument> documents
    ) {
        String directory = "farm-ownership/"
                + farmProfileId
                + "/"
                + UUID.randomUUID();
        List<StoredOwnershipDocument> storedDocuments = new ArrayList<>();

        try {
            for (OwnershipDocumentValidator.ValidatedDocument document : documents) {
                FileStorage.StoredFile storedFile = fileStorage.store(
                        directory,
                        document.source(),
                        document.storageExtension(),
                        document.size()
                );
                storedDocuments.add(new StoredOwnershipDocument(
                        document.originalFilename(),
                        storedFile.storageKey(),
                        document.contentType(),
                        storedFile.sizeBytes(),
                        storedFile.sha256()
                ));
                if (storedFile.sizeBytes() != document.size()
                        || !storedFile.sha256().equals(document.sha256())) {
                    throw new IllegalStateException(
                            "검증한 파일과 저장된 파일의 내용이 일치하지 않습니다."
                    );
                }
            }
            return List.copyOf(storedDocuments);
        } catch (RuntimeException exception) {
            deleteQuietly(storedDocuments);
            throw FarmOwnershipException.storageFailure();
        }
    }

    private void deleteQuietly(List<StoredOwnershipDocument> documents) {
        for (int index = documents.size() - 1; index >= 0; index--) {
            try {
                fileStorage.delete(documents.get(index).storageKey());
            } catch (RuntimeException exception) {
                log.error(
                        "Failed to clean up an ownership document. storageKey={}",
                        documents.get(index).storageKey(),
                        exception
                );
            }
        }
    }
}
