package chungbuk.cityfarmerplus.application.controller;

import chungbuk.cityfarmerplus.application.service.FarmCandidateService;
import chungbuk.cityfarmerplus.auth.config.SecurityConfig;
import chungbuk.cityfarmerplus.auth.exception.GlobalExceptionHandler;
import chungbuk.cityfarmerplus.farm.exception.FarmProfileException;
import chungbuk.cityfarmerplus.farm.exception.FarmProfileExceptionHandler;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FarmCandidateController.class)
@Import({
        FarmProfileExceptionHandler.class,
        GlobalExceptionHandler.class,
        SecurityConfig.class
})
class FarmCandidateControllerWebTest {

    private static final String ENDPOINT =
            "/api/farm/job-postings/101/applications";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FarmCandidateService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void missingFarmProfileUsesFarmProfileErrorContract() throws Exception {
        when(jwtDecoder.decode("farm-jwt")).thenReturn(farmJwt());
        when(service.getCandidates(15L, 101L))
                .thenThrow(FarmProfileException.profileNotFound());

        mockMvc.perform(get(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farm-jwt"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FARM_PROFILE_NOT_FOUND"));
    }

    private Jwt farmJwt() {
        Instant issuedAt = Instant.now();
        return Jwt.withTokenValue("farm-jwt")
                .header("alg", "HS256")
                .subject("15")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(3600))
                .claim("role", "FARM")
                .build();
    }
}
