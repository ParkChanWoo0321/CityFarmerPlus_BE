package chungbuk.cityfarmerplus.auth.service;

import chungbuk.cityfarmerplus.auth.dto.LoginRequest;
import chungbuk.cityfarmerplus.auth.dto.SignupRequest;
import chungbuk.cityfarmerplus.auth.dto.UserResponse;
import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.exception.AuthException;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder);
    }

    @Test
    void signupEncodesPasswordAndCreatesActiveUser() {
        SignupRequest request = new SignupRequest(
                "farm_user",
                "password123!",
                "농가 사용자",
                User.UserType.FARM
        );
        when(userRepository.existsByLoginIdIgnoreCase("farm_user")).thenReturn(false);
        when(passwordEncoder.encode("password123!")).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 1L);
            return user;
        });

        UserResponse response = authService.signup(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.userType()).isEqualTo(User.UserType.FARM);
        assertThat(response.accountStatus()).isEqualTo(User.AccountStatus.ACTIVE);
        verify(passwordEncoder).encode("password123!");
    }

    @Test
    void signupRejectsDuplicateLoginId() {
        SignupRequest request = new SignupRequest(
                "duplicate",
                "password123!",
                "중복 사용자",
                User.UserType.URBAN_FARMER
        );
        when(userRepository.existsByLoginIdIgnoreCase("duplicate")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo("DUPLICATE_LOGIN_ID");
    }

    @Test
    void publicSignupRejectsManagerRole() {
        SignupRequest request = new SignupRequest(
                "manager_user",
                "password123!",
                "담당자",
                User.UserType.CENTER_ADMIN
        );

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo("MANAGER_SIGNUP_NOT_ALLOWED");
    }

    @Test
    void loginReturnsUserWhenCredentialsMatch() {
        User user = User.register(
                "farm_user",
                "encoded-password",
                "농가 사용자",
                User.UserType.FARM
        );
        ReflectionTestUtils.setField(user, "id", 1L);
        when(userRepository.findByLoginIdIgnoreCase("farm_user"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123!", "encoded-password"))
                .thenReturn(true);

        AuthService.LoginResult result = authService.login(
                new LoginRequest("farm_user", "password123!")
        );

        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.userType()).isEqualTo(User.UserType.FARM);
    }

    @Test
    void loginUsesSameErrorForUnknownIdAndWrongPassword() {
        when(userRepository.findByLoginIdIgnoreCase("unknown"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                authService.login(new LoginRequest("unknown", "wrong-password"))
        )
                .isInstanceOf(AuthException.class)
                .hasMessage("아이디 또는 비밀번호가 일치하지 않습니다.");
    }
}
