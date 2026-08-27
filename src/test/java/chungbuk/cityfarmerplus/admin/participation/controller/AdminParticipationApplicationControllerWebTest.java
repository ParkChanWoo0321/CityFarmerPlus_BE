package chungbuk.cityfarmerplus.admin.participation.controller;

import chungbuk.cityfarmerplus.admin.participation.dto.ParticipationRejectRequest;
import chungbuk.cityfarmerplus.admin.participation.service.AdminParticipationApplicationService;
import chungbuk.cityfarmerplus.auth.config.SecurityConfig;
import chungbuk.cityfarmerplus.auth.exception.GlobalExceptionHandler;
import chungbuk.cityfarmerplus.urbanfarmer.participation.dto.ParticipationApplicationResponse;
import chungbuk.cityfarmerplus.urbanfarmer.participation.entity.ParticipationApplication;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminParticipationApplicationController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class AdminParticipationApplicationControllerWebTest {

    private static final String ENDPOINT = "/api/admin/participation-applications";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminParticipationApplicationService applicationService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void centerAdminListsSubmittedApplications() throws Exception {
        authorizeAdmin();
        when(applicationService.list()).thenReturn(List.of(response(
                ParticipationApplication.ParticipationStatus.SUBMITTED,
                null
        )));

        mockMvc.perform(get(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(100))
                .andExpect(jsonPath("$[0].urbanFarmerId").value(21))
                .andExpect(jsonPath("$[0].status").value("SUBMITTED"));

        verify(applicationService).list();
    }

    @Test
    void centerAdminGetsApplicationDetail() throws Exception {
        authorizeAdmin();
        when(applicationService.getDetail(100L)).thenReturn(response(
                ParticipationApplication.ParticipationStatus.SUBMITTED,
                null
        ));

        mockMvc.perform(get(ENDPOINT + "/100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.programYear").value(2026));

        verify(applicationService).getDetail(100L);
    }

    @Test
    void centerAdminApprovesWithJwtSubjectAsReviewerId() throws Exception {
        authorizeAdmin();
        when(applicationService.approve(30L, 100L)).thenReturn(response(
                ParticipationApplication.ParticipationStatus.APPROVED,
                null
        ));

        mockMvc.perform(post(ENDPOINT + "/100/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        verify(applicationService).approve(30L, 100L);
    }

    @Test
    void centerAdminRejectsWithValidatedReasonAndJwtSubject() throws Exception {
        authorizeAdmin();
        when(applicationService.reject(
                eq(30L),
                eq(100L),
                argThat(request -> "자격 서류 미비".equals(request.reason()))
        )).thenReturn(response(
                ParticipationApplication.ParticipationStatus.REJECTED,
                "자격 서류 미비"
        ));

        mockMvc.perform(post(ENDPOINT + "/100/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content("""
                                {"reason":"자격 서류 미비"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("자격 서류 미비"));

        verify(applicationService).reject(
                eq(30L),
                eq(100L),
                argThat(request -> "자격 서류 미비".equals(request.reason()))
        );
    }

    @Test
    void blankRejectReasonIsRejectedBeforeServiceCall() throws Exception {
        authorizeAdmin();

        mockMvc.perform(post(ENDPOINT + "/100/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void missingJwtCannotReviewApplications() throws Exception {
        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void nonAdminCannotReviewApplications() throws Exception {
        when(jwtDecoder.decode("farm-jwt")).thenReturn(jwt("15", "FARM"));

        mockMvc.perform(post(ENDPOINT + "/100/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farm-jwt"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(applicationService);
    }

    private void authorizeAdmin() {
        when(jwtDecoder.decode("admin-jwt")).thenReturn(jwt("30", "CENTER_ADMIN"));
    }

    private ParticipationApplicationResponse response(
            ParticipationApplication.ParticipationStatus status,
            String rejectionReason
    ) {
        Instant now = Instant.parse("2026-08-16T00:00:00Z");
        return new ParticipationApplicationResponse(
                100L,
                21L,
                "도시농부",
                2026,
                true,
                "평일 근무 희망",
                status,
                30L,
                rejectionReason,
                now,
                now,
                null,
                1L,
                now,
                now
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
