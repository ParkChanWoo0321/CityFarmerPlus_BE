package chungbuk.cityfarmerplus.jobposting.service;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.exception.AuthException;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import chungbuk.cityfarmerplus.farm.exception.FarmProfileException;
import chungbuk.cityfarmerplus.farm.repository.FarmProfileRepository;
import chungbuk.cityfarmerplus.jobposting.exception.JobPostingException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobPostingAccessService {

    private final UserRepository userRepository;
    private final FarmProfileRepository farmProfileRepository;

    public FarmProfile requireApprovedFarm(Long userId) {
        FarmProfile profile = requireFarmProfile(userId);
        if (profile.getStatus() != FarmProfile.FarmProfileStatus.APPROVED) {
            throw JobPostingException.farmApprovalRequired();
        }
        return profile;
    }

    public FarmProfile requireApprovedFarmForUpdate(Long userId) {
        FarmProfile profile = requireFarmProfileForUpdate(userId);
        if (profile.getStatus() != FarmProfile.FarmProfileStatus.APPROVED) {
            throw JobPostingException.farmApprovalRequired();
        }
        return profile;
    }

    public FarmProfile requireFarmProfileForUpdate(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(AuthException::userNotFound);
        if (!user.isActive()) {
            throw AuthException.inactiveAccount();
        }
        if (user.getUserType() != User.UserType.FARM) {
            throw FarmProfileException.farmRoleRequired();
        }
        return farmProfileRepository.findByOwnerIdForUpdate(userId)
                .orElseThrow(FarmProfileException::profileNotFound);
    }

    public FarmProfile requireFarmProfile(Long userId) {
        User user = requireActiveUser(userId);
        if (user.getUserType() != User.UserType.FARM) {
            throw FarmProfileException.farmRoleRequired();
        }
        return farmProfileRepository.findByOwnerId(userId)
                .orElseThrow(FarmProfileException::profileNotFound);
    }

    public User requireActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(AuthException::userNotFound);
        if (!user.isActive()) {
            throw AuthException.inactiveAccount();
        }
        return user;
    }

}
