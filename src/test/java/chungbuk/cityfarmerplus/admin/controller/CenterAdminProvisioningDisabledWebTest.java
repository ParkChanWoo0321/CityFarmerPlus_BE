package chungbuk.cityfarmerplus.admin.controller;

import chungbuk.cityfarmerplus.admin.config.AdminProvisioningConfig;
import chungbuk.cityfarmerplus.admin.service.CenterAdminProvisioningService;
import chungbuk.cityfarmerplus.auth.config.SecurityConfig;
import chungbuk.cityfarmerplus.auth.exception.GlobalExceptionHandler;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
        "app.admin-provisioning.enabled=false",
        "app.admin-provisioning.key="
})
class CenterAdminProvisioningDisabledWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void disabledProvisioningReturnsForbidden() throws Exception {
        mockMvc.perform(post("/api/internal/center-admins")
                        .header(
                                CenterAdminProvisioningController.PROVISIONING_KEY_HEADER,
                                "center-admin-web-test-key-with-at-least-32-bytes"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content("""
                                {
                                  "loginId": "center_admin",
                                  "password": "admin-password-123",
                                  "name": "충북 담당자"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROVISIONING_DISABLED"));
    }
}
