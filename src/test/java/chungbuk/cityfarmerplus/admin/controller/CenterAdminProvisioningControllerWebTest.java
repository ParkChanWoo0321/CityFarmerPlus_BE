package chungbuk.cityfarmerplus.admin.controller;

import chungbuk.cityfarmerplus.admin.dto.CenterAdminProvisioningRequest;
import chungbuk.cityfarmerplus.admin.service.CenterAdminProvisioningService;
import chungbuk.cityfarmerplus.auth.config.SecurityConfig;
import chungbuk.cityfarmerplus.auth.dto.UserResponse;
import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CenterAdminProvisioningController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class CenterAdminProvisioningControllerWebTest {

    private static final String ENDPOINT = "/api/internal/center-admins";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CenterAdminProvisioningService provisioningService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void validProvisioningKeyAndRequestCreateCenterAdminWithoutJwt() throws Exception {
        when(provisioningService.provision(
                eq("provisioning-key"),
                argThat(request -> "center_admin".equals(request.loginId())
                        && "센터 관리자".equals(request.name()))
        )).thenReturn(new UserResponse(
                30L,
                "center_admin",
                "센터 관리자",
                null,
                null,
                null,
                User.UserType.CENTER_ADMIN,
                User.AccountStatus.ACTIVE
        ));

        mockMvc.perform(post(ENDPOINT)
                        .header("X-Admin-Provisioning-Key", "provisioning-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(validRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(30))
                .andExpect(jsonPath("$.loginId").value("center_admin"))
                .andExpect(jsonPath("$.userType").value("CENTER_ADMIN"))
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"));

        verify(provisioningService).provision(
                eq("provisioning-key"),
                argThat(request -> "center_admin".equals(request.loginId()))
        );
    }

    @Test
    void missingProvisioningKeyIsRejectedBeforeServiceCall() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_REQUEST_HEADER"));

        verifyNoInteractions(provisioningService);
    }

    @Test
    void invalidProvisioningRequestIsRejectedBeforeServiceCall() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .header("X-Admin-Provisioning-Key", "provisioning-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content("""
                                {
                                  "loginId": "BAD ID",
                                  "password": "short",
                                  "name": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(provisioningService);
    }

    private String validRequestJson() {
        return """
                {
                  "loginId": "center_admin",
                  "password": "safe-password-1234",
                  "name": "센터 관리자"
                }
                """;
    }
}
