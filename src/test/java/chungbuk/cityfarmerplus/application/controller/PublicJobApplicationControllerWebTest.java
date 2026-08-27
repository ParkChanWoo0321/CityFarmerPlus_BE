package chungbuk.cityfarmerplus.application.controller;

import chungbuk.cityfarmerplus.application.dto.JobApplicationResponse;
import chungbuk.cityfarmerplus.application.entity.JobApplication;
import chungbuk.cityfarmerplus.application.service.JobApplicationService;
import chungbuk.cityfarmerplus.auth.config.SecurityConfig;
import chungbuk.cityfarmerplus.auth.exception.GlobalExceptionHandler;
import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicJobApplicationController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class PublicJobApplicationControllerWebTest {

    private static final String ENDPOINT = "/api/job-postings/101/applications";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobApplicationService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void urbanFarmerAppliesWithJwtSubjectAndCreatedStatus() throws Exception {
        when(jwtDecoder.decode("urban-jwt")).thenReturn(jwt("21", "URBAN_FARMER"));
        when(service.apply(21L, 101L)).thenReturn(response(
                JobApplication.ApplicationStatus.APPLIED,
                null
        ));

        mockMvc.perform(post(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(200))
                .andExpect(jsonPath("$.jobPostingId").value(101))
                .andExpect(jsonPath("$.postingTitle").value("사과 수확 작업"))
                .andExpect(jsonPath("$.status").value("APPLIED"));

        verify(service).apply(21L, 101L);
    }

    @Test
    void missingJwtCannotApply() throws Exception {
        mockMvc.perform(post(ENDPOINT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(service);
    }

    @Test
    void nonUrbanFarmerCannotApply() throws Exception {
        when(jwtDecoder.decode("farm-jwt")).thenReturn(jwt("15", "FARM"));

        mockMvc.perform(post(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farm-jwt"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(service);
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
