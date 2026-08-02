package chungbuk.cityfarmerplus.auth.service;

import chungbuk.cityfarmerplus.auth.dto.LoginIdAvailabilityResponse;
import chungbuk.cityfarmerplus.auth.dto.LoginRequest;
import chungbuk.cityfarmerplus.auth.dto.SignupRequest;
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

    public record LoginResult(
            Long userId,
            User.UserType userType,
            UserResponse user
    ) {
    }
}
