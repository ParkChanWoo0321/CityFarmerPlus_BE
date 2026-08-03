package chungbuk.cityfarmerplus.admin.service;

import chungbuk.cityfarmerplus.admin.config.AdminProvisioningProperties;
import chungbuk.cityfarmerplus.admin.dto.CenterAdminCreateRequest;
import chungbuk.cityfarmerplus.auth.dto.UserResponse;
import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.exception.AuthException;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CenterAdminProvisioningService {

    private final AdminProvisioningProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse provision(
            String providedProvisioningKey,
            CenterAdminCreateRequest request
    ) {
        verifyProvisioningKey(providedProvisioningKey);

        String loginId = request.loginId().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByLoginIdIgnoreCase(loginId)) {
            throw AuthException.duplicateLoginId();
        }

        User centerAdmin = User.registerCenterAdmin(
                loginId,
                passwordEncoder.encode(request.password()),
                request.name().trim()
        );

        try {
            return UserResponse.from(userRepository.saveAndFlush(centerAdmin));
        } catch (DataIntegrityViolationException exception) {
            throw AuthException.duplicateLoginId();
        }
    }

    private void verifyProvisioningKey(String providedProvisioningKey) {
        if (!properties.enabled()) {
            throw AuthException.adminProvisioningDisabled();
        }

        byte[] expected = properties.key().getBytes(StandardCharsets.UTF_8);
        byte[] provided = providedProvisioningKey == null
                ? new byte[0]
                : providedProvisioningKey.getBytes(StandardCharsets.UTF_8);

        if (!MessageDigest.isEqual(expected, provided)) {
            throw AuthException.invalidAdminProvisioningKey();
        }
    }
}
