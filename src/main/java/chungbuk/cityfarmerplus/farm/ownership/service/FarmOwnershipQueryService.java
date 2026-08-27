package chungbuk.cityfarmerplus.farm.ownership.service;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.exception.AuthException;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import chungbuk.cityfarmerplus.common.storage.FileStorage;
import chungbuk.cityfarmerplus.common.storage.FileStorageException;
import chungbuk.cityfarmerplus.farm.exception.FarmProfileException;
import chungbuk.cityfarmerplus.farm.ownership.dto.FarmOwnershipDocumentDownload;
import chungbuk.cityfarmerplus.farm.ownership.dto.FarmOwnershipSubmissionResponse;
import chungbuk.cityfarmerplus.farm.ownership.entity.FarmOwnershipDocument;
import chungbuk.cityfarmerplus.farm.ownership.entity.FarmOwnershipSubmission;
import chungbuk.cityfarmerplus.farm.ownership.exception.FarmOwnershipException;
import chungbuk.cityfarmerplus.farm.ownership.repository.FarmOwnershipDocumentRepository;
import chungbuk.cityfarmerplus.farm.ownership.repository.FarmOwnershipSubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FarmOwnershipQueryService {

    private final UserRepository userRepository;
    private final FarmOwnershipSubmissionRepository submissionRepository;
    private final FarmOwnershipDocumentRepository documentRepository;
    private final FileStorage fileStorage;

    public List<FarmOwnershipSubmissionResponse> getMine(Long userId) {
        validateActiveFarm(userId);
        return submissionRepository.findAllDetailedByOwnerId(userId)
                .stream()
                .map(submission -> FarmOwnershipSubmissionResponse.from(
                        submission,
                        submission.getFarmProfile().getStatus()
                ))
                .toList();
    }

    public FarmOwnershipSubmissionResponse getMine(
            Long userId,
            Long submissionId
    ) {
        validateActiveFarm(userId);
        FarmOwnershipSubmission submission = submissionRepository
                .findDetailedByIdAndOwnerId(submissionId, userId)
                .orElseThrow(FarmOwnershipException::submissionNotFound);
        return FarmOwnershipSubmissionResponse.from(
                submission,
                submission.getFarmProfile().getStatus()
        );
    }

    public FarmOwnershipDocumentDownload download(Long userId, Long documentId) {
        User requester = getActiveUser(userId);
        FarmOwnershipDocument document = documentRepository.findById(documentId)
                .orElseThrow(FarmOwnershipException::documentNotFound);
        validateDownloadAuthority(requester, document);

        return loadDocument(document);
    }

    public FarmOwnershipDocumentDownload downloadForAdmin(
            Long profileId,
            Long documentId
    ) {
        FarmOwnershipDocument document = documentRepository
                .findByIdAndSubmissionFarmProfileId(documentId, profileId)
                .orElseThrow(FarmOwnershipException::documentNotFound);
        return loadDocument(document);
    }

    private FarmOwnershipDocumentDownload loadDocument(
            FarmOwnershipDocument document
    ) {
        try {
            Resource resource = fileStorage.load(document.getStorageKey());
            if (!resource.exists() || !resource.isReadable()) {
                throw FarmOwnershipException.documentReadFailure();
            }
            return new FarmOwnershipDocumentDownload(
                    resource,
                    document.getOriginalFilename(),
                    document.getContentType(),
                    document.getSizeBytes()
            );
        } catch (FileStorageException exception) {
            throw FarmOwnershipException.documentReadFailure();
        }
    }

    private void validateActiveFarm(Long userId) {
        User user = getActiveUser(userId);
        if (user.getUserType() != User.UserType.FARM) {
            throw FarmProfileException.farmRoleRequired();
        }
    }

    private User getActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(AuthException::userNotFound);
        if (!user.isActive()) {
            throw AuthException.inactiveAccount();
        }
        return user;
    }

    private void validateDownloadAuthority(
            User requester,
            FarmOwnershipDocument document
    ) {
        if (!document.getSubmission().getFarmProfile().getOwner().isActive()) {
            throw FarmOwnershipException.documentReadFailure();
        }
        Long ownerId = document.getSubmission()
                .getFarmProfile()
                .getOwner()
                .getId();
        if (requester.getUserType() != User.UserType.FARM
                || !ownerId.equals(requester.getId())) {
            throw FarmOwnershipException.documentAccessDenied();
        }
    }
}
