package chungbuk.cityfarmerplus.support.controller;

import chungbuk.cityfarmerplus.auth.config.SecurityConfig;
import chungbuk.cityfarmerplus.auth.exception.GlobalExceptionHandler;
import chungbuk.cityfarmerplus.support.dto.FaqResponse;
import chungbuk.cityfarmerplus.support.service.FaqService;
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
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FaqController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class FaqControllerWebTest {

    private static final String ENDPOINT = "/api/support/faqs";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FaqService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void authenticatedAccountReadsFaqsInServiceOrder() throws Exception {
        when(jwtDecoder.decode("farm-jwt")).thenReturn(jwt("15", "FARM"));
        when(service.getAll()).thenReturn(List.of(
                new FaqResponse("회원", "가입할 수 있나요?", "네, 가입할 수 있습니다."),
                new FaqResponse("교육", "어떤 파일을 제출하나요?", "PDF 또는 이미지 파일입니다.")
        ));

        mockMvc.perform(get(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farm-jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value("회원"))
                .andExpect(jsonPath("$[0].question").value("가입할 수 있나요?"))
                .andExpect(jsonPath("$[0].answer").value("네, 가입할 수 있습니다."))
                .andExpect(jsonPath("$[1].category").value("교육"))
                .andExpect(jsonPath("$[1].question").value("어떤 파일을 제출하나요?"))
                .andExpect(jsonPath("$[1].answer").value("PDF 또는 이미지 파일입니다."));

        verify(service).getAll();
    }

    @Test
    void emptyFaqListIsReturnedAsAnEmptyJsonArray() throws Exception {
        when(jwtDecoder.decode("urban-jwt")).thenReturn(jwt("20", "URBAN_FARMER"));
        when(service.getAll()).thenReturn(List.of());

        mockMvc.perform(get(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        verify(service).getAll();
    }

    @Test
    void anonymousUserReadsFaqList() throws Exception {
        when(service.getAll()).thenReturn(List.of(
                new FaqResponse("회원", "가입할 수 있나요?", "네, 가입할 수 있습니다.")
        ));

        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value("회원"));

        verify(service).getAll();
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
