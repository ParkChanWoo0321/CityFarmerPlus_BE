package chungbuk.cityfarmerplus.application.controller;

import chungbuk.cityfarmerplus.application.dto.JobApplicationResponse;
import chungbuk.cityfarmerplus.application.entity.JobApplication;
import chungbuk.cityfarmerplus.application.service.JobApplicationService;
import chungbuk.cityfarmerplus.auth.config.SecurityConfig;
import chungbuk.cityfarmerplus.auth.exception.GlobalExceptionHandler;
import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import chungbuk.cityfarmerplus.common.web.PageResponse;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UrbanFarmerJobApplicationController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class UrbanFarmerJobApplicationControllerWebTest {

    private static final String ENDPOINT = "/api/urban-farmers/me/job-applications";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobApplicationService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void urbanFarmerListsOwnApplicationsWithStablePageContract() throws Exception {
        authorizeUrbanFarmer();
        when(service.getMine(21L, 1, 5)).thenReturn(new PageResponse<>(
                List.of(response(JobApplication.ApplicationStatus.APPLIED, null)),
                1,
                5,
                11,
                3,
                true
        ));

        mockMvc.perform(get(ENDPOINT)
                        .queryParam("page", "1")
                        .queryParam("size", "5")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(200))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.pageable").doesNotExist());

        verify(service).getMine(21L, 1, 5);
    }

    @Test
    void urbanFarmerGetsOwnedApplicationDetail() throws Exception {
        authorizeUrbanFarmer();
        when(service.getMine(21L, 200L)).thenReturn(response(
                JobApplication.ApplicationStatus.APPLIED,
                null
        ));

        mockMvc.perform(get(ENDPOINT + "/200")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(200))
                .andExpect(jsonPath("$.status").value("APPLIED"));

        verify(service).getMine(21L, 200L);
    }

    @Test
    void urbanFarmerWithdrawsOwnedApplication() throws Exception {
        authorizeUrbanFarmer();
        Instant withdrawnAt = Instant.parse("2026-08-27T01:00:00Z");
        when(service.withdraw(21L, 200L)).thenReturn(response(
                JobApplication.ApplicationStatus.WITHDRAWN,
                withdrawnAt
        ));

        mockMvc.perform(post(ENDPOINT + "/200/withdraw")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.withdrawnAt").value("2026-08-27T01:00:00Z"));

        verify(service).withdraw(21L, 200L);
    }

    @Test
    void invalidPageAndSizeAreRejectedBeforeServiceCall() throws Exception {
        authorizeUrbanFarmer();

        mockMvc.perform(get(ENDPOINT)
                        .queryParam("page", "-1")
                        .queryParam("size", "101")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(service);
    }

    @Test
    void missingJwtCannotReadApplications() throws Exception {
        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(service);
    }

    @Test
    void nonUrbanFarmerCannotReadApplications() throws Exception {
        when(jwtDecoder.decode("admin-jwt")).thenReturn(jwt("30", "CENTER_ADMIN"));

        mockMvc.perform(get(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-jwt"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(service);
    }

    private void authorizeUrbanFarmer() {
        when(jwtDecoder.decode("urban-jwt")).thenReturn(jwt("21", "URBAN_FARMER"));
    }

    private JobApplicationResponse response(
            JobApplication.ApplicationStatus status,
            Instant withdrawnAt
    ) {
        return new JobApplicationResponse(
                200L,
                101L,
                "사과 수확 작업",
                "충주 사과농원",
                ChungbukCityCounty.CHUNGJU,
                LocalDate.of(2026, 9, 1),
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                status,
                JobApplication.FarmOpinion.NONE,
                null,
                120_000,
                "DAILY",
                Instant.parse("2026-08-27T00:00:00Z"),
                withdrawnAt,
                null
        );
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
