package chungbuk.cityfarmerplus.auth.service;

import chungbuk.cityfarmerplus.auth.dto.LoginIdAvailabilityResponse;
import chungbuk.cityfarmerplus.auth.dto.LoginRequest;
import chungbuk.cityfarmerplus.auth.dto.SignupRequest;
import chungbuk.cityfarmerplus.auth.dto.UserProfileUpdateRequest;
import chungbuk.cityfarmerplus.auth.dto.UserResponse;
import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.exception.AuthException;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse signup(SignupRequest request) {
        if (request.userType() == User.UserType.CENTER_ADMIN) {
            throw AuthException.managerSignupNotAllowed();
        }

        String loginId = normalizeLoginId(request.loginId());
        if (userRepository.existsByLoginIdIgnoreCase(loginId)) {
            throw AuthException.duplicateLoginId();
        }

        User user = User.register(
                loginId,
                passwordEncoder.encode(request.password()),
                request.name().trim(),
                request.userType()
        );
        user.updatePhoneNumber(normalizeOptionalPhoneNumber(request.phoneNumber()));
        user.updateBirthDate(request.birthDate());
        user.updateAddress(request.address());

        try {
            return UserResponse.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException exception) {
            throw AuthException.duplicateLoginId();
        }
    }

    public LoginResult login(LoginRequest request) {
        String loginId = normalizeLoginId(request.loginId());
        User user = userRepository.findByLoginIdIgnoreCase(loginId)
                .orElseThrow(AuthException::invalidCredentials);

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw AuthException.invalidCredentials();
        }
        if (!user.isActive()) {
            throw AuthException.inactiveAccount();
        }

        return new LoginResult(
                user.getId(),
                user.getUserType(),
                UserResponse.from(user)
        );
    }

    public UserResponse getById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(AuthException::userNotFound);
        if (!user.isActive()) {
            throw AuthException.inactiveAccount();
        }
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateProfile(
            Long userId,
            UserProfileUpdateRequest request
    ) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(AuthException::userNotFound);
        if (!user.isActive()) {
            throw AuthException.inactiveAccount();
        }

        if (request.name() != null) {
            user.updateName(request.name());
        }
        if (request.phoneNumber() != null) {
            user.updatePhoneNumber(normalizeOptionalPhoneNumber(request.phoneNumber()));
        }
        if (request.birthDate() != null) {
            user.updateBirthDate(request.birthDate());
        }
        if (request.address() != null) {
            user.updateAddress(request.address());
        }

        return UserResponse.from(user);
    }

    public LoginIdAvailabilityResponse checkLoginIdAvailability(String loginId) {
        String normalizedLoginId = normalizeLoginId(loginId);
        boolean available = !userRepository.existsByLoginIdIgnoreCase(normalizedLoginId);
        return new LoginIdAvailabilityResponse(normalizedLoginId, available);
    }

    private String normalizeLoginId(String loginId) {
        if (loginId == null) {
            return "";
        }
        return loginId.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOptionalPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return null;
        }
        return phoneNumber.replaceAll("\\D", "");
    }

    public record LoginResult(
            Long userId,
            User.UserType userType,
            UserResponse user
    ) {
    }
}
