package chungbuk.cityfarmerplus.education.progress.controller;

import chungbuk.cityfarmerplus.auth.config.SecurityConfig;
import chungbuk.cityfarmerplus.auth.exception.GlobalExceptionHandler;
import chungbuk.cityfarmerplus.education.progress.dto.EducationEnrollmentResponse;
import chungbuk.cityfarmerplus.education.progress.entity.EducationEnrollment;
import chungbuk.cityfarmerplus.education.progress.security.EducationProgressWebhookVerifier;
import chungbuk.cityfarmerplus.education.progress.service.EducationProgressEventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EducationProgressWebhookController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class EducationProgressWebhookControllerWebTest {

    private static final String ENDPOINT =
            "/api/integrations/education/progress-events";
    private static final String BODY = """
            {
              "provider": "CHUNGBUK_LMS",
              "eventId": "evt-1",
              "externalEnrollmentId": "enrollment-21-1",
              "urbanFarmerId": 21,
              "courseId": 1,
              "totalMinutes": 480,
              "completedMinutes": 240,
              "occurredAt": "2026-08-28T00:00:00Z"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EducationProgressWebhookVerifier verifier;

    @MockitoBean
    private EducationProgressEventService eventService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void signedProviderRequestDoesNotRequireUserJwt() throws Exception {
        when(verifier.verify(anyString(), anyString(), any(byte[].class)))
                .thenReturn("a".repeat(64));
        when(eventService.ingest(any(), anyString())).thenReturn(response());

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(EducationProgressWebhookController.TIMESTAMP_HEADER, "1787875200")
                        .header(EducationProgressWebhookController.SIGNATURE_HEADER,
                                "sha256=" + "0".repeat(64))
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urbanFarmerId").value(21))
                .andExpect(jsonPath("$.progressStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.progressPercentage").value(50));
    }

    @Test
    void malformedSignedJsonIsRejectedBeforeServiceCall() throws Exception {
        when(verifier.verify(anyString(), anyString(), any(byte[].class)))
                .thenReturn("a".repeat(64));

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(EducationProgressWebhookController.TIMESTAMP_HEADER, "1787875200")
                        .header(EducationProgressWebhookController.SIGNATURE_HEADER,
                                "sha256=" + "0".repeat(64))
                        .content("{not-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_EDUCATION_PROGRESS_EVENT"));

        verifyNoInteractions(eventService);
    }

    @Test
    void missingSignatureHeadersAreRejected() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_REQUEST_HEADER"));

        verifyNoInteractions(verifier, eventService);
    }

    private EducationEnrollmentResponse response() {
        Instant timestamp = Instant.parse("2026-08-28T00:00:00Z");
        return new EducationEnrollmentResponse(
                100L,
                21L,
                1L,
                "CHUNGBUK_LMS",
                "enrollment-21-1",
                EducationEnrollment.ProgressStatus.IN_PROGRESS,
                480,
                240,
                240,
                50,
                timestamp,
                null,
                timestamp,
                timestamp,
                0L
        );
    }
}
