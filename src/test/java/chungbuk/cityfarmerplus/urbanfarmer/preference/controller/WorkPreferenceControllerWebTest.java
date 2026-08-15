package chungbuk.cityfarmerplus.urbanfarmer.preference.controller;

import chungbuk.cityfarmerplus.auth.config.SecurityConfig;
import chungbuk.cityfarmerplus.auth.exception.GlobalExceptionHandler;
import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import chungbuk.cityfarmerplus.urbanfarmer.preference.dto.WorkPreferenceRequest;
import chungbuk.cityfarmerplus.urbanfarmer.preference.dto.WorkPreferenceResponse;
import chungbuk.cityfarmerplus.urbanfarmer.preference.service.WorkPreferenceService;
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

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkPreferenceController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class WorkPreferenceControllerWebTest {

    private static final String ENDPOINT =
            "/api/urban-farmers/me/work-preference";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkPreferenceService preferenceService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void urbanFarmerUpsertsPreferenceUsingIsoPreferredDates() throws Exception {
        LocalDate preferredStartDate = LocalDate.of(2026, 8, 20);
        LocalDate preferredEndDate = LocalDate.of(2026, 8, 31);
        when(jwtDecoder.decode("urban-jwt"))
                .thenReturn(jwt("21", "URBAN_FARMER"));
        when(preferenceService.upsert(
                eq(21L),
                argThat(request -> preferredStartDate.equals(request.preferredStartDate())
                        && preferredEndDate.equals(request.preferredEndDate()))
        )).thenReturn(response(preferredStartDate, preferredEndDate));

        mockMvc.perform(put(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(validRequestJson("2026-08-20", "2026-08-31")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.preferredStartDate").value("2026-08-20"))
                .andExpect(jsonPath("$.preferredEndDate").value("2026-08-31"));

        verify(preferenceService).upsert(
                eq(21L),
                argThat(request -> preferredStartDate.equals(request.preferredStartDate())
                        && preferredEndDate.equals(request.preferredEndDate()))
        );
    }

    @Test
    void sameStartAndEndDateIsAccepted() throws Exception {
        LocalDate sameDate = LocalDate.of(2026, 8, 20);
        when(jwtDecoder.decode("urban-jwt"))
                .thenReturn(jwt("21", "URBAN_FARMER"));
        when(preferenceService.upsert(
                eq(21L),
                argThat(request -> sameDate.equals(request.preferredStartDate())
                        && sameDate.equals(request.preferredEndDate()))
        )).thenReturn(response(sameDate, sameDate));

        mockMvc.perform(put(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(validRequestJson("2026-08-20", "2026-08-20")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredStartDate").value("2026-08-20"))
                .andExpect(jsonPath("$.preferredEndDate").value("2026-08-20"));

        verify(preferenceService).upsert(
                eq(21L),
                argThat(request -> sameDate.equals(request.preferredStartDate())
                        && sameDate.equals(request.preferredEndDate()))
        );
    }

    @Test
    void endDateBeforeStartDateIsRejectedBeforeServiceCall() throws Exception {
        when(jwtDecoder.decode("urban-jwt"))
                .thenReturn(jwt("21", "URBAN_FARMER"));

        mockMvc.perform(put(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(validRequestJson("2026-08-20", "2026-08-19")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(preferenceService);
    }

    @Test
    void malformedPreferredDateIsRejectedBeforeServiceCall() throws Exception {
        when(jwtDecoder.decode("urban-jwt"))
                .thenReturn(jwt("21", "URBAN_FARMER"));

        mockMvc.perform(put(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(validRequestJson("2026/08/20", "2026-08-31")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(preferenceService);
    }

    private String validRequestJson(
            String preferredStartDate,
            String preferredEndDate
    ) {
        return """
                {
                  "preferredRegions": ["CHUNGJU"],
                  "availableDays": ["MONDAY", "WEDNESDAY"],
                  "availableWorkTypes": ["수확", "선별"],
                  "preferredStartDate": "%s",
                  "preferredEndDate": "%s",
                  "canTravel": true,
                  "notes": "충주 지역 근무를 희망합니다."
                }
                """.formatted(preferredStartDate, preferredEndDate);
    }

    private WorkPreferenceResponse response(
            LocalDate preferredStartDate,
            LocalDate preferredEndDate
    ) {
        Instant now = Instant.parse("2026-08-12T01:00:00Z");
        return new WorkPreferenceResponse(
                100L,
                21L,
                List.of(ChungbukCityCounty.CHUNGJU),
                List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                List.of("수확", "선별"),
                preferredStartDate,
                preferredEndDate,
                true,
                "충주 지역 근무를 희망합니다.",
                0L,
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
