package chungbuk.cityfarmerplus.ai.jobposting;

import chungbuk.cityfarmerplus.auth.config.SecurityConfig;
import chungbuk.cityfarmerplus.auth.exception.GlobalExceptionHandler;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiJobPostingController.class)
@Import({
        GlobalExceptionHandler.class,
        SecurityConfig.class
})
class AiJobPostingControllerWebTest {

    private static final String ENDPOINT = "/api/ai/job-posting-previews";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiJobPostingService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void farmRequestsEditablePreview() throws Exception {
        when(jwtDecoder.decode("farm-jwt")).thenReturn(jwt("15", "FARM"));
        when(service.preview(eq(15L), any(AiJobPostingPreviewRequest.class)))
                .thenReturn(previewResponse());

        mockMvc.perform(post(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farm-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(validRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("감자 수확 작업자를 모집합니다"))
                .andExpect(jsonPath("$.generator").value("RULE_BASED_V1"));

        verify(service).preview(
                eq(15L),
                any(AiJobPostingPreviewRequest.class)
        );
    }

    @Test
    void nonFarmRoleIsForbiddenBeforeServiceCall() throws Exception {
        when(jwtDecoder.decode("urban-farmer-jwt"))
                .thenReturn(jwt("21", "URBAN_FARMER"));

        mockMvc.perform(post(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-farmer-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(service);
    }

    @Test
    void missingTokenIsUnauthorizedBeforeServiceCall() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(service);
    }

    @Test
    void invalidRequestIsRejectedBeforeServiceCall() throws Exception {
        when(jwtDecoder.decode("farm-jwt")).thenReturn(jwt("15", "FARM"));

        mockMvc.perform(post(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farm-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson().replace("\"crop\": \"감자\"", "\"crop\": \" \"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(service);
    }

    private String validRequestJson() {
        return """
                {
                  "crop": "감자",
                  "workType": "수확",
                  "workDate": "2099-08-20",
                  "startTime": "09:00:00",
                  "endTime": "16:00:00",
                  "capacity": 3,
                  "meetingPlace": "농장 입구",
                  "supplies": null,
                  "precautions": null
                }
                """;
    }

    private AiJobPostingPreviewResponse previewResponse() {
        return new AiJobPostingPreviewResponse(
                "감자 수확 작업자를 모집합니다",
                "감자 수확 작업을 함께할 도시농부를 모집합니다.",
                "작업 장갑",
                "안전거리를 유지해 주세요.",
                "농가의 설명을 먼저 들어 주세요.",
                "RULE_BASED_V1"
        );
    }

    private Jwt jwt(String subject, String role) {
        Instant issuedAt = Instant.now();
        return Jwt.withTokenValue(role.toLowerCase() + "-jwt")
                .header("alg", "HS256")
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(3600))
                .claim("role", role)
                .build();
    }
}
