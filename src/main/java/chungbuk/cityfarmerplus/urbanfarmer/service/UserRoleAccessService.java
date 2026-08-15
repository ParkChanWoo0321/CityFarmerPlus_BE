package chungbuk.cityfarmerplus.urbanfarmer.service;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.exception.AuthException;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import chungbuk.cityfarmerplus.common.exception.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserRoleAccessService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public User requireUrbanFarmer(Long userId) {
        return require(userId, User.UserType.URBAN_FARMER);
    }

    @Transactional
    public User requireUrbanFarmerForUpdate(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(AuthException::userNotFound);
        return validate(user, User.UserType.URBAN_FARMER);
    }

    private User require(Long userId, User.UserType requiredType) {
        User user = userRepository.findById(userId)
                .orElseThrow(AuthException::userNotFound);
        return validate(user, requiredType);
    }

    private User validate(User user, User.UserType requiredType) {
        if (!user.isActive()) {
            throw AuthException.inactiveAccount();
        }
        if (user.getUserType() != requiredType) {
            throw new DomainException(
                    HttpStatus.FORBIDDEN,
                    "URBAN_FARMER_ROLE_REQUIRED",
                    "도시농부 계정만 사용할 수 있습니다."
            );
        }
        return user;
    }
}
