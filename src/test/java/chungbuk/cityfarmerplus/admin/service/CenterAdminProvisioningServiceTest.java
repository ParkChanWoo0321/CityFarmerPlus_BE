package chungbuk.cityfarmerplus.admin.service;

import chungbuk.cityfarmerplus.admin.config.AdminProvisioningProperties;
import chungbuk.cityfarmerplus.admin.dto.CenterAdminCreateRequest;
import chungbuk.cityfarmerplus.auth.dto.UserResponse;
import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.exception.AuthException;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CenterAdminProvisioningServiceTest {

    private static final String VALID_KEY =
            "center-admin-provisioning-test-key-with-32-bytes";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private CenterAdminProvisioningService provisioningService;

    @BeforeEach
    void setUp() {
        provisioningService = new CenterAdminProvisioningService(
                new AdminProvisioningProperties(true, VALID_KEY),
                userRepository,
                passwordEncoder
        );
    }

    @Test
    void validKeyCreatesActiveCenterAdminWithEncodedPassword() {
        CenterAdminCreateRequest request = request("center_admin");
        when(userRepository.existsByLoginIdIgnoreCase("center_admin")).thenReturn(false);
        when(passwordEncoder.encode("admin-password-123"))
                .thenReturn("encoded-admin-password");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 10L);
            return user;
        });

        UserResponse response = provisioningService.provision(VALID_KEY, request);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.userType()).isEqualTo(User.UserType.CENTER_ADMIN);
        assertThat(response.accountStatus()).isEqualTo(User.AccountStatus.ACTIVE);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertThat(userCaptor.getValue().getPassword()).isEqualTo("encoded-admin-password");
    }

    @Test
    void missingKeyIsRejectedBeforeRepositoryAccess() {
        assertThatThrownBy(() -> provisioningService.provision(null, request("center_admin")))
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo("INVALID_PROVISIONING_KEY");

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void wrongKeyIsRejectedBeforeRepositoryAccess() {
        assertThatThrownBy(() ->
                provisioningService.provision("wrong-key", request("center_admin"))
        )
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo("INVALID_PROVISIONING_KEY");

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void disabledProvisioningIsRejected() {
        CenterAdminProvisioningService disabledService =
                new CenterAdminProvisioningService(
                        new AdminProvisioningProperties(false, ""),
                        userRepository,
                        passwordEncoder
                );

        assertThatThrownBy(() -> disabledService.provision(VALID_KEY, request("center_admin")))
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo("PROVISIONING_DISABLED");

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void duplicateLoginIdIsRejected() {
        when(userRepository.existsByLoginIdIgnoreCase("center_admin")).thenReturn(true);

        assertThatThrownBy(() ->
                provisioningService.provision(VALID_KEY, request("center_admin"))
        )
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo("DUPLICATE_LOGIN_ID");

        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void databaseUniqueConstraintRaceIsReturnedAsDuplicateLoginId() {
        when(userRepository.existsByLoginIdIgnoreCase("center_admin")).thenReturn(false);
        when(passwordEncoder.encode("admin-password-123"))
                .thenReturn("encoded-admin-password");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate login id"));

        assertThatThrownBy(() ->
                provisioningService.provision(VALID_KEY, request("center_admin"))
        )
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo("DUPLICATE_LOGIN_ID");
    }

    private CenterAdminCreateRequest request(String loginId) {
        return new CenterAdminCreateRequest(
                loginId,
                "admin-password-123",
                "충북 담당자"
        );
    }
}
