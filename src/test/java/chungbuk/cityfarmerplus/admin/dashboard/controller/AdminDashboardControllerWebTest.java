package chungbuk.cityfarmerplus.admin.dashboard.controller;

import chungbuk.cityfarmerplus.admin.dashboard.dto.AdminDashboardResponse;
import chungbuk.cityfarmerplus.admin.dashboard.service.AdminDashboardService;
import chungbuk.cityfarmerplus.auth.config.SecurityConfig;
import chungbuk.cityfarmerplus.auth.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminDashboardController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class AdminDashboardControllerWebTest {

    private static final String ENDPOINT = "/api/admin/dashboard";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminDashboardService dashboardService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void centerAdminGetsDashboardCounts() throws Exception {
        when(jwtDecoder.decode("admin-jwt")).thenReturn(jwt("30", "CENTER_ADMIN"));
        when(dashboardService.getDashboard()).thenReturn(new AdminDashboardResponse(
                1,
                2,
                3,
                4,
                5,
                6,
                7,
                8,
                9,
                10,
                11
        ));

        mockMvc.perform(get(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submittedParticipationApplications").value(1))
                .andExpect(jsonPath("$.pendingEducationSubmissions").value(2))
                .andExpect(jsonPath("$.pendingFarmOwnershipSubmissions").value(3))
                .andExpect(jsonPath("$.pendingJobPostings").value(4))
                .andExpect(jsonPath("$.openJobPostings").value(5))
                .andExpect(jsonPath("$.pendingJobApplications").value(6))
                .andExpect(jsonPath("$.scheduledWorkAssignments").value(7))
                .andExpect(jsonPath("$.completedWorkAssignments").value(8))
                .andExpect(jsonPath("$.activeUrbanFarmerCount").value(9))
                .andExpect(jsonPath("$.activeFarmCount").value(10))
                .andExpect(jsonPath("$.activeCenterAdminCount").value(11));

        verify(dashboardService).getDashboard();
    }

    @Test
    void missingJwtCannotReadDashboard() throws Exception {
        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(dashboardService);
    }

    @Test
    void nonAdminCannotReadDashboard() throws Exception {
        when(jwtDecoder.decode("urban-jwt")).thenReturn(jwt("21", "URBAN_FARMER"));

        mockMvc.perform(get(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(dashboardService);
    }

    private Jwt jwt(String subject, String role) {
        Instant issuedAt = Instant.now();
        return Jwt.withTokenValue(role + "-jwt")
                .header("alg", "HS256")
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(3600))
                .claim("role", role)
                .build();
    }
}
