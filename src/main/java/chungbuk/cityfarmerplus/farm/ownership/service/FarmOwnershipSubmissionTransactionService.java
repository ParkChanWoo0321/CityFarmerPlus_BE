package chungbuk.cityfarmerplus.farm.ownership.service;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.exception.AuthException;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import chungbuk.cityfarmerplus.farm.exception.FarmProfileException;
import chungbuk.cityfarmerplus.farm.ownership.dto.FarmOwnershipSubmissionResponse;
import chungbuk.cityfarmerplus.farm.ownership.entity.FarmOwnershipSubmission;
import chungbuk.cityfarmerplus.farm.ownership.exception.FarmOwnershipException;
import chungbuk.cityfarmerplus.farm.ownership.repository.FarmOwnershipSubmissionRepository;
import chungbuk.cityfarmerplus.farm.repository.FarmProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FarmOwnershipSubmissionTransactionService {

    private final FarmProfileRepository farmProfileRepository;
    private final FarmOwnershipSubmissionRepository submissionRepository;
    private final UserRepository userRepository;

    @Transactional
    public FarmOwnershipSubmissionResponse persist(
            Long userId,
            List<StoredOwnershipDocument> documents
    ) {
        if (documents == null || documents.isEmpty()) {
            throw FarmOwnershipException.documentsRequired();
        }

        User owner = userRepository.findByIdForUpdate(userId)
                .orElseThrow(AuthException::userNotFound);
        validateActiveFarmOwner(owner);
        FarmProfile profile = farmProfileRepository.findByOwnerIdForUpdate(userId)
                .orElseThrow(FarmProfileException::profileNotFound);

        if (!profile.canSubmitOwnershipDocuments()) {
            throw FarmOwnershipException.submissionNotAllowed();
        }

        int attemptNumber = submissionRepository
                .findMaxAttemptNumberByFarmProfileId(profile.getId()) + 1;
        FarmOwnershipSubmission submission = FarmOwnershipSubmission.createPending(
                profile,
                attemptNumber
        );
        documents.forEach(document -> submission.addDocument(
                document.originalFilename(),
                document.storageKey(),
                document.contentType(),
                document.sizeBytes(),
                document.sha256()
        ));
        profile.markOwnershipReviewPending();

        try {
            FarmOwnershipSubmission savedSubmission = submissionRepository
                    .saveAndFlush(submission);
            return FarmOwnershipSubmissionResponse.from(
                    savedSubmission,
                    profile.getStatus()
            );
        } catch (DataIntegrityViolationException exception) {
            throw FarmOwnershipException.dataConflict();
        }
    }

    private void validateActiveFarmOwner(User owner) {
        if (!owner.isActive()) {
            throw AuthException.inactiveAccount();
        }
        if (owner.getUserType() != User.UserType.FARM) {
            throw FarmProfileException.farmRoleRequired();
        }
    }
}
