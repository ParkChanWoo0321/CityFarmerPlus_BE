package chungbuk.cityfarmerplus.ai.support;

import chungbuk.cityfarmerplus.auth.config.SecurityConfig;
import chungbuk.cityfarmerplus.auth.exception.GlobalExceptionHandler;
import chungbuk.cityfarmerplus.common.web.PageResponse;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiSupportController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class AiSupportControllerWebTest {

    private static final String ENDPOINT = "/api/ai/support/messages";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiSupportService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void authenticatedAccountSendsQuestionAndReceivesPersistedAnswer() throws Exception {
        Instant createdAt = Instant.parse("2026-08-20T03:04:05Z");
        when(jwtDecoder.decode("farm-jwt"))
                .thenReturn(jwt("15", "FARM", "farm-jwt"));
        when(service.send(
                15L,
                new SupportMessageRequest("농가 모집 공고 작성 방법")
        )).thenReturn(new SupportMessageResponse(
                71L,
                "농가 모집 공고 작성 방법",
                "농가 공고",
                "승인된 농가는 공고를 작성할 수 있습니다.",
                false,
                createdAt
        ));

        mockMvc.perform(post(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farm-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content("""
                                {"message":"농가 모집 공고 작성 방법"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(71))
                .andExpect(jsonPath("$.question").value("농가 모집 공고 작성 방법"))
                .andExpect(jsonPath("$.category").value("농가 공고"))
                .andExpect(jsonPath("$.answer")
                        .value("승인된 농가는 공고를 작성할 수 있습니다."))
                .andExpect(jsonPath("$.officialConfirmationRequired").value(false))
                .andExpect(jsonPath("$.createdAt").value("2026-08-20T03:04:05Z"));

        verify(service).send(
                15L,
                new SupportMessageRequest("농가 모집 공고 작성 방법")
        );
    }

    @Test
    void authenticatedAccountReadsOnlyItsPagedHistory() throws Exception {
        Instant createdAt = Instant.parse("2026-08-20T04:05:06Z");
        when(jwtDecoder.decode("urban-jwt"))
                .thenReturn(jwt("20", "URBAN_FARMER", "urban-jwt"));
        when(service.getMine(20L, 1, 5)).thenReturn(new PageResponse<>(
                List.of(new SupportMessageResponse(
                        81L,
                        "교육 문의",
                        "교육",
                        "교육 안내입니다.",
                        false,
                        createdAt
                )),
                1,
                5,
                6,
                2,
                false
        ));

        mockMvc.perform(get(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt")
                        .queryParam("page", "1")
                        .queryParam("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(81))
                .andExpect(jsonPath("$.content[0].question").value("교육 문의"))
                .andExpect(jsonPath("$.content[0].category").value("교육"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(false));

        verify(service).getMine(20L, 1, 5);
    }

    @Test
    void blankQuestionIsRejectedBeforeServiceCall() throws Exception {
        when(jwtDecoder.decode("farm-jwt"))
                .thenReturn(jwt("15", "FARM", "farm-jwt"));

        mockMvc.perform(post(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farm-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("문의 내용은 필수입니다."));

        verifyNoInteractions(service);
    }

    @Test
    void sendingQuestionRequiresAuthentication() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"교육 문의"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(service);
    }

    @Test
    void readingHistoryRequiresAuthentication() throws Exception {
        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(service);
    }

    private Jwt jwt(String subject, String role, String tokenValue) {
        Instant issuedAt = Instant.now();
        return Jwt.withTokenValue(tokenValue)
                .header("alg", "HS256")
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(3600))
                .claim("role", role)
                .build();
    }
}
