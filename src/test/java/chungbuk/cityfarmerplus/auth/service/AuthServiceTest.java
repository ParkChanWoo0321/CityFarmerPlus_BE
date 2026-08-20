package chungbuk.cityfarmerplus.auth.service;

import chungbuk.cityfarmerplus.auth.dto.LoginRequest;
import chungbuk.cityfarmerplus.auth.dto.SignupRequest;
import chungbuk.cityfarmerplus.auth.dto.UserProfileUpdateRequest;
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

import java.time.LocalDate;
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
        LocalDate birthDate = LocalDate.of(1985, 3, 14);
        SignupRequest request = new SignupRequest(
                "farm_user",
                "password123!",
                "농가 사용자",
                User.UserType.FARM,
                "010-1234-5678",
                birthDate,
                " 충청북도 청주시 "
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
        assertThat(response.phoneNumber()).isEqualTo("01012345678");
        assertThat(response.birthDate()).isEqualTo(birthDate);
        assertThat(response.address()).isEqualTo("충청북도 청주시");
        verify(passwordEncoder).encode("password123!");
    }

    @Test
    void signupKeepsOptionalPersonalInformationNullable() {
        SignupRequest request = new SignupRequest(
                "urban_user",
                "password123!",
                "도시농부",
                User.UserType.URBAN_FARMER
        );
        when(userRepository.existsByLoginIdIgnoreCase("urban_user")).thenReturn(false);
        when(passwordEncoder.encode("password123!")).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 2L);
            return user;
        });

        UserResponse response = authService.signup(request);

        assertThat(response.phoneNumber()).isNull();
        assertThat(response.birthDate()).isNull();
        assertThat(response.address()).isNull();
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

    @Test
    void activeUserUpdatesOnlyEditableProfileFieldsWithNormalizedPhoneNumber() {
        User user = User.register(
                "farm_user",
                "encoded-password",
                "기존 이름",
                User.UserType.FARM
        );
        ReflectionTestUtils.setField(user, "id", 1L);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        LocalDate birthDate = LocalDate.of(1990, 5, 20);

        UserResponse response = authService.updateProfile(
                1L,
                new UserProfileUpdateRequest(
                        " 변경 이름 ",
                        "010-1234-5678",
                        birthDate,
                        " 충청북도 청주시 예시로 1 "
                )
        );

        assertThat(response.loginId()).isEqualTo("farm_user");
        assertThat(response.name()).isEqualTo("변경 이름");
        assertThat(response.phoneNumber()).isEqualTo("01012345678");
        assertThat(response.birthDate()).isEqualTo(birthDate);
        assertThat(response.address()).isEqualTo("충청북도 청주시 예시로 1");
        assertThat(response.userType()).isEqualTo(User.UserType.FARM);
        assertThat(response.accountStatus()).isEqualTo(User.AccountStatus.ACTIVE);
        verify(userRepository).findByIdForUpdate(1L);
    }

    @Test
    void blankOptionalProfileValuesClearPhoneAndAddress() {
        User user = User.register(
                "urban_user",
                "encoded-password",
                "도시농부",
                User.UserType.URBAN_FARMER
        );
        user.updatePhoneNumber("01012345678");
        user.updateAddress("기존 주소");
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(user));

        UserResponse response = authService.updateProfile(
                2L,
                new UserProfileUpdateRequest(null, "", null, "   ")
        );

        assertThat(response.phoneNumber()).isNull();
        assertThat(response.address()).isNull();
        assertThat(response.name()).isEqualTo("도시농부");
    }

    @Test
    void inactiveUserCannotUpdateProfileEvenWithAStillValidJwt() {
        User withdrawnUser = User.register(
                "withdrawn_user",
                "encoded-password",
                "탈퇴 사용자",
                User.UserType.URBAN_FARMER
        );
        ReflectionTestUtils.setField(
                withdrawnUser,
                "accountStatus",
                User.AccountStatus.WITHDRAWN
        );
        when(userRepository.findByIdForUpdate(3L))
                .thenReturn(Optional.of(withdrawnUser));

        assertThatThrownBy(() -> authService.updateProfile(
                3L,
                new UserProfileUpdateRequest("새 이름", null, null, null)
        ))
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo("INACTIVE_ACCOUNT");
    }
}
