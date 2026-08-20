package chungbuk.cityfarmerplus.auth.controller;

import chungbuk.cityfarmerplus.auth.config.SecurityConfig;
import chungbuk.cityfarmerplus.auth.dto.SignupRequest;
import chungbuk.cityfarmerplus.auth.dto.UserProfileUpdateRequest;
import chungbuk.cityfarmerplus.auth.dto.UserResponse;
import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.exception.GlobalExceptionHandler;
import chungbuk.cityfarmerplus.auth.jwt.JwtTokenProvider;
import chungbuk.cityfarmerplus.auth.service.AccountWithdrawalService;
import chungbuk.cityfarmerplus.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({
        GlobalExceptionHandler.class,
        SecurityConfig.class
})
class AuthControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private AccountWithdrawalService accountWithdrawalService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void authenticatedUserUpdatesEditableProfileFields() throws Exception {
        when(jwtDecoder.decode("urban-jwt")).thenReturn(jwt("15", "URBAN_FARMER"));
        when(authService.updateProfile(
                eq(15L),
                any(UserProfileUpdateRequest.class)
        )).thenReturn(userResponse());

        mockMvc.perform(patch("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content("""
                                {
                                  "name": "변경 이름",
                                  "phoneNumber": "010-1234-5678",
                                  "birthDate": "1990-05-20",
                                  "address": "충청북도 청주시 예시로 1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("urban_user"))
                .andExpect(jsonPath("$.name").value("변경 이름"))
                .andExpect(jsonPath("$.phoneNumber").value("01012345678"))
                .andExpect(jsonPath("$.birthDate").value("1990-05-20"))
                .andExpect(jsonPath("$.address").value("충청북도 청주시 예시로 1"))
                .andExpect(jsonPath("$.userType").value("URBAN_FARMER"));

        verify(authService).updateProfile(
                eq(15L),
                any(UserProfileUpdateRequest.class)
        );
    }

    @Test
    void profileUpdateRequiresJwtAndValidPhoneNumber() throws Exception {
        mockMvc.perform(patch("/api/auth/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        when(jwtDecoder.decode("urban-jwt")).thenReturn(jwt("15", "URBAN_FARMER"));
        mockMvc.perform(patch("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber":"010-123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(authService);
    }

    @Test
    void authenticatedUserWithdrawsAfterPasswordConfirmation() throws Exception {
        when(jwtDecoder.decode("urban-jwt")).thenReturn(jwt("15", "URBAN_FARMER"));

        mockMvc.perform(post("/api/auth/withdrawal")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"password123!"}
                                """))
                .andExpect(status().isNoContent());

        verify(accountWithdrawalService).withdraw(15L, "password123!");
    }

    @Test
    void withdrawalRejectsMissingPasswordBeforeServiceCall() throws Exception {
        when(jwtDecoder.decode("urban-jwt")).thenReturn(jwt("15", "URBAN_FARMER"));

        mockMvc.perform(post("/api/auth/withdrawal")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(accountWithdrawalService);
    }

    @Test
    void publicSignupRejectsPasswordLongerThanBcryptUtf8Limit() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(signupJson("가".repeat(25))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(
                        "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다."
                ));

        verify(authService, never()).signup(any(SignupRequest.class));
    }

    private UserResponse userResponse() {
        return new UserResponse(
                15L,
                "urban_user",
                "변경 이름",
                "01012345678",
                LocalDate.of(1990, 5, 20),
                "충청북도 청주시 예시로 1",
                User.UserType.URBAN_FARMER,
                User.AccountStatus.ACTIVE
        );
    }

    private String signupJson(String password) {
        return """
                {
                  "loginId": "urban_user",
                  "password": "%s",
                  "name": "도시농부",
                  "userType": "URBAN_FARMER"
                }
                """.formatted(password);
    }

    private Jwt jwt(String subject, String role) {
        Instant issuedAt = Instant.now();
        return Jwt.withTokenValue("jwt")
                .header("alg", "HS256")
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(3600))
                .claim("role", role)
                .build();
    }
}
