package chungbuk.cityfarmerplus.dashboard.controller;

import chungbuk.cityfarmerplus.auth.config.SecurityConfig;
import chungbuk.cityfarmerplus.auth.exception.GlobalExceptionHandler;
import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import chungbuk.cityfarmerplus.dashboard.dto.FarmHomeResponse;
import chungbuk.cityfarmerplus.dashboard.dto.UrbanFarmerHomeResponse;
import chungbuk.cityfarmerplus.dashboard.service.FarmHomeService;
import chungbuk.cityfarmerplus.dashboard.service.UrbanFarmerHomeService;
import chungbuk.cityfarmerplus.education.entity.EducationCertification;
import chungbuk.cityfarmerplus.farm.exception.FarmProfileException;
import chungbuk.cityfarmerplus.urbanfarmer.participation.entity.ParticipationApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HomeDashboardController.class)
@Import({
        GlobalExceptionHandler.class,
        SecurityConfig.class
})
class HomeDashboardControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UrbanFarmerHomeService urbanFarmerHomeService;

    @MockitoBean
    private FarmHomeService farmHomeService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void urbanFarmerHomeExposesApplicationAndWorkPreferenceSummary() throws Exception {
        Instant submittedAt = Instant.parse("2026-08-11T01:00:00Z");
        when(jwtDecoder.decode("urban-jwt")).thenReturn(jwt("21", "URBAN_FARMER"));
        when(urbanFarmerHomeService.get(21L)).thenReturn(new UrbanFarmerHomeResponse(
                EducationCertification.CertificationStatus.PENDING_REVIEW,
                31L,
                ParticipationApplication.ParticipationStatus.SUBMITTED,
                2026,
                submittedAt,
                true,
                List.of(ChungbukCityCounty.CHUNGJU),
                List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                List.of(),
                List.of()
        ));

        mockMvc.perform(get("/api/urban-farmers/me/home")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestParticipationApplicationId").value(31))
                .andExpect(jsonPath("$.latestParticipationStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.participationSubmittedAt")
                        .value("2026-08-11T01:00:00Z"))
                .andExpect(jsonPath("$.preferredRegions[0]").value("CHUNGJU"))
                .andExpect(jsonPath("$.availableDays[0]").value("MONDAY"));

        verify(urbanFarmerHomeService).get(21L);
    }

    @Test
    void farmHomeExposesDesignStatusCounts() throws Exception {
        when(jwtDecoder.decode("farm-jwt")).thenReturn(jwt("15", "FARM"));
        when(farmHomeService.get(15L)).thenReturn(new FarmHomeResponse(
                null,
                Map.of("DRAFT", 2L, "OPEN", 3L),
                Map.of("PENDING", 1L, "APPROVED", 3L, "CLOSED", 4L, "REJECTED", 1L),
                List.of(),
                List.of()
        ));

        mockMvc.perform(get("/api/farm/me/home")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farm-jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayPostingCounts.PENDING").value(1))
                .andExpect(jsonPath("$.displayPostingCounts.APPROVED").value(3))
                .andExpect(jsonPath("$.displayPostingCounts.CLOSED").value(4))
                .andExpect(jsonPath("$.displayPostingCounts.REJECTED").value(1));

        verify(farmHomeService).get(15L);
    }

    @Test
    void missingFarmProfileUsesFarmProfileErrorContract() throws Exception {
        when(jwtDecoder.decode("farm-jwt")).thenReturn(jwt("15", "FARM"));
        when(farmHomeService.get(15L))
                .thenThrow(FarmProfileException.profileNotFound());

        mockMvc.perform(get("/api/farm/me/home")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farm-jwt"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FARM_PROFILE_NOT_FOUND"));
    }

    @Test
    void homeRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/urban-farmers/me/home"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(urbanFarmerHomeService, farmHomeService);
    }

    @Test
    void farmCannotReadUrbanFarmerHome() throws Exception {
        when(jwtDecoder.decode("farm-jwt")).thenReturn(jwt("15", "FARM"));

        mockMvc.perform(get("/api/urban-farmers/me/home")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farm-jwt"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(urbanFarmerHomeService, farmHomeService);
    }

    @Test
    void urbanFarmerCannotReadFarmHome() throws Exception {
        when(jwtDecoder.decode("urban-jwt")).thenReturn(jwt("21", "URBAN_FARMER"));

        mockMvc.perform(get("/api/farm/me/home")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(urbanFarmerHomeService, farmHomeService);
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
