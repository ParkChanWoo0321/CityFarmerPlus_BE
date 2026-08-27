package chungbuk.cityfarmerplus.admin.service;

import chungbuk.cityfarmerplus.admin.dto.CenterAdminProvisioningRequest;
import chungbuk.cityfarmerplus.auth.dto.UserResponse;
import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.exception.AuthException;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CenterAdminProvisioningService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.provisioning-key:}")
    private String provisioningKey;

    public UserResponse provision(String requestKey, CenterAdminProvisioningRequest request) {
        if (provisioningKey == null || provisioningKey.isBlank()) {
            throw AuthException.adminProvisioningDisabled();
        }
        if (!provisioningKey.equals(requestKey)) {
            throw AuthException.invalidAdminProvisioningKey();
        }

        String loginId = request.loginId().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByLoginIdIgnoreCase(loginId)) {
            throw AuthException.duplicateLoginId();
        }

        User user = User.registerCenterAdmin(
                loginId,
                passwordEncoder.encode(request.password()),
                request.name().trim()
        );

        return UserResponse.from(userRepository.saveAndFlush(user));
    }
}
