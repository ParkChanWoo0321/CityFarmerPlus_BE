package chungbuk.cityfarmerplus.admin.controller;

import chungbuk.cityfarmerplus.admin.config.AdminProvisioningConfig;
import chungbuk.cityfarmerplus.admin.service.CenterAdminProvisioningService;
import chungbuk.cityfarmerplus.auth.config.SecurityConfig;
import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.exception.GlobalExceptionHandler;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CenterAdminProvisioningController.class)
@Import({
        AdminProvisioningConfig.class,
        CenterAdminProvisioningService.class,
        GlobalExceptionHandler.class,
        SecurityConfig.class
})
@TestPropertySource(properties = {
        "app.admin-provisioning.enabled=true",
        "app.admin-provisioning.key=center-admin-web-test-key-with-at-least-32-bytes"
})
class CenterAdminProvisioningWebTest {

    private static final String ENDPOINT = "/api/internal/center-admins";
    private static final String VALID_KEY =
            "center-admin-web-test-key-with-at-least-32-bytes";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUpRepository() {
        when(userRepository.existsByLoginIdIgnoreCase("center_admin")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 10L);
            return user;
        });
    }

    @Test
    void validProvisioningKeyCreatesCenterAdminWithoutJwt() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .header(
                                CenterAdminProvisioningController.PROVISIONING_KEY_HEADER,
                                VALID_KEY
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(requestJson("admin-password-123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.userType").value("CENTER_ADMIN"))
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.provisioningKey").doesNotExist());
    }

    @Test
    void missingAndWrongKeysReturnTheSameUnauthorizedError() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(requestJson("admin-password-123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_PROVISIONING_KEY"));

        mockMvc.perform(post(ENDPOINT)
                        .header(
                                CenterAdminProvisioningController.PROVISIONING_KEY_HEADER,
                                "wrong-key"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(requestJson("admin-password-123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_PROVISIONING_KEY"));

        verifyNoInteractions(jwtDecoder);
    }

    @Test
    void jwtWithoutProvisioningKeyIsStillRejected() throws Exception {
        when(jwtDecoder.decode("center-admin-jwt")).thenReturn(centerAdminJwt());

        mockMvc.perform(post(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer center-admin-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(requestJson("admin-password-123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_PROVISIONING_KEY"));
    }

    @Test
    void onlyTheExactProvisioningPathIsPublic() throws Exception {
        mockMvc.perform(post(ENDPOINT + "/extra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void bcryptBoundaryAcceptsSeventyTwoUtf8Bytes() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .header(
                                CenterAdminProvisioningController.PROVISIONING_KEY_HEADER,
                                VALID_KEY
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(requestJson("가".repeat(24))))
                .andExpect(status().isCreated());
    }

    @Test
    void bcryptBoundaryRejectsMoreThanSeventyTwoUtf8Bytes() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .header(
                                CenterAdminProvisioningController.PROVISIONING_KEY_HEADER,
                                VALID_KEY
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(requestJson("가".repeat(25))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(
                        "담당자 비밀번호는 UTF-8 기준 72바이트 이하여야 합니다."
                ));
    }

    private String requestJson(String password) {
        return """
                {
                  "loginId": "center_admin",
                  "password": "%s",
                  "name": "충북 담당자"
                }
                """.formatted(password);
    }

    private Jwt centerAdminJwt() {
        Instant issuedAt = Instant.now();
        return Jwt.withTokenValue("center-admin-jwt")
                .header("alg", "HS256")
                .subject("10")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(3600))
                .claim("role", "CENTER_ADMIN")
                .build();
    }
}
