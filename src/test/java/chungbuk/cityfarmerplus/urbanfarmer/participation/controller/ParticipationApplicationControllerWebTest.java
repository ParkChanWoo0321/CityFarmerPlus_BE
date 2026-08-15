package chungbuk.cityfarmerplus.urbanfarmer.participation.controller;

import chungbuk.cityfarmerplus.auth.config.SecurityConfig;
import chungbuk.cityfarmerplus.auth.exception.GlobalExceptionHandler;
import chungbuk.cityfarmerplus.urbanfarmer.participation.dto.ParticipationApplicationResponse;
import chungbuk.cityfarmerplus.urbanfarmer.participation.entity.ParticipationApplication;
import chungbuk.cityfarmerplus.urbanfarmer.participation.service.ParticipationApplicationService;
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

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ParticipationApplicationController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class ParticipationApplicationControllerWebTest {

    private static final String ENDPOINT =
            "/api/urban-farmers/me/participation-applications";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ParticipationApplicationService applicationService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void urbanFarmerCreatesDraftApplicationWithCreatedStatus() throws Exception {
        when(jwtDecoder.decode("urban-jwt")).thenReturn(jwt("21", "URBAN_FARMER"));
        when(applicationService.create(
                eq(21L),
                argThat(request -> request.programYear() == 2026
                        && request.agriculturalBusinessRegistered()
                        && "평일 근무 희망".equals(request.applicationNote()))
        )).thenReturn(response(
                ParticipationApplication.ParticipationStatus.DRAFT,
                null,
                null
        ));

        mockMvc.perform(post(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(validRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.urbanFarmerId").value(21))
                .andExpect(jsonPath("$.programYear").value(2026))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        verify(applicationService).create(
                eq(21L),
                argThat(request -> request.programYear() == 2026
                        && request.agriculturalBusinessRegistered())
        );
    }

    @Test
    void missingJwtCannotCreateApplication() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(applicationService);
    }

    @Test
    void farmAccountCannotCreateUrbanFarmerApplication() throws Exception {
        when(jwtDecoder.decode("farm-jwt")).thenReturn(jwt("31", "FARM"));

        mockMvc.perform(post(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farm-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(applicationService);
    }

    @Test
    void invalidProgramYearIsRejectedBeforeServiceCall() throws Exception {
        when(jwtDecoder.decode("urban-jwt")).thenReturn(jwt("21", "URBAN_FARMER"));

        mockMvc.perform(post(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(validRequestJson().replace("2026", "1999")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void urbanFarmerSubmitsOwnedApplicationThroughSubmitRoute() throws Exception {
        when(jwtDecoder.decode("urban-jwt")).thenReturn(jwt("21", "URBAN_FARMER"));
        when(applicationService.submit(21L, 100L)).thenReturn(response(
                ParticipationApplication.ParticipationStatus.SUBMITTED,
                Instant.parse("2026-08-16T01:00:00Z"),
                null
        ));

        mockMvc.perform(post(ENDPOINT + "/100/submit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.submittedAt")
                        .value("2026-08-16T01:00:00Z"));

        verify(applicationService).submit(21L, 100L);
    }

    @Test
    void urbanFarmerCancelsOwnedApplicationThroughCancelRoute() throws Exception {
        when(jwtDecoder.decode("urban-jwt")).thenReturn(jwt("21", "URBAN_FARMER"));
        when(applicationService.cancel(21L, 100L)).thenReturn(response(
                ParticipationApplication.ParticipationStatus.CANCELLED,
                Instant.parse("2026-08-16T01:00:00Z"),
                Instant.parse("2026-08-16T02:00:00Z")
        ));

        mockMvc.perform(post(ENDPOINT + "/100/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt")
                        .value("2026-08-16T02:00:00Z"));

        verify(applicationService).cancel(21L, 100L);
    }

    private String validRequestJson() {
        return """
                {
                  "programYear": 2026,
                  "agriculturalBusinessRegistered": true,
                  "applicationNote": "평일 근무 희망"
                }
                """;
    }

    private ParticipationApplicationResponse response(
            ParticipationApplication.ParticipationStatus status,
            Instant submittedAt,
            Instant cancelledAt
    ) {
        Instant createdAt = Instant.parse("2026-08-16T00:00:00Z");
        return new ParticipationApplicationResponse(
                100L,
                21L,
                "도시농부",
                2026,
                true,
                "평일 근무 희망",
                status,
                null,
                null,
                submittedAt,
                null,
                cancelledAt,
                0L,
                createdAt,
                createdAt
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
