package chungbuk.cityfarmerplus.work.controller;

import chungbuk.cityfarmerplus.auth.config.SecurityConfig;
import chungbuk.cityfarmerplus.auth.exception.GlobalExceptionHandler;
import chungbuk.cityfarmerplus.farm.exception.FarmProfileException;
import chungbuk.cityfarmerplus.work.service.WorkAssignmentService;
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

@WebMvcTest(FarmWorkAssignmentController.class)
@Import({
        GlobalExceptionHandler.class,
        SecurityConfig.class
})
class FarmWorkAssignmentControllerWebTest {

    private static final String ENDPOINT = "/api/farm/work-assignments";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkAssignmentService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void missingFarmProfileUsesGlobalDomainErrorContract() throws Exception {
        when(jwtDecoder.decode("farm-jwt")).thenReturn(farmJwt());
        when(service.getFarmAssignments(15L, 0, 20))
                .thenThrow(FarmProfileException.profileNotFound());

        mockMvc.perform(get(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farm-jwt"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FARM_PROFILE_NOT_FOUND"));
    }

    private Jwt farmJwt() {
        Instant issuedAt = Instant.parse("2026-08-13T00:00:00Z");
        return Jwt.withTokenValue("farm-jwt")
                .header("alg", "HS256")
                .subject("15")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(3600))
                .claim("role", "FARM")
                .build();
    }
}
