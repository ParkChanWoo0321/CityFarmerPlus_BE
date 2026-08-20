package chungbuk.cityfarmerplus.urbanfarmer.participation.controller;

import chungbuk.cityfarmerplus.auth.config.SecurityConfig;
import chungbuk.cityfarmerplus.auth.exception.GlobalExceptionHandler;
import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import chungbuk.cityfarmerplus.urbanfarmer.participation.dto.ParticipationFormResponse;
import chungbuk.cityfarmerplus.urbanfarmer.participation.service.ParticipationFormService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ParticipationFormController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class ParticipationFormControllerWebTest {

    private static final String ENDPOINT =
            "/api/urban-farmers/me/participation-forms/2026";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ParticipationFormService formService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void getsNotStartedFormForProgramYear() throws Exception {
        when(jwtDecoder.decode("urban-jwt")).thenReturn(jwt("21", "URBAN_FARMER"));
        when(formService.getMine(21L, 2026)).thenReturn(
                response(ParticipationFormResponse.ParticipationFormStatus.NOT_STARTED)
        );

        mockMvc.perform(get(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.programYear").value(2026))
                .andExpect(jsonPath("$.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.nextAction").value("SUBMIT"))
                .andExpect(jsonPath("$.editableFields[0]")
                        .value("agriculturalBusinessRegistered"));
    }

    @Test
    void savesCompositeFormWithIsoDatesAndExpectedVersions() throws Exception {
        when(jwtDecoder.decode("urban-jwt")).thenReturn(jwt("21", "URBAN_FARMER"));
        when(formService.save(
                eq(21L),
                eq(2026),
                argThat(request -> LocalDate.of(2026, 8, 20)
                                .equals(request.preferredStartDate())
                        && request.expectedApplicationVersion() == 4L)
        )).thenReturn(response(ParticipationFormResponse.ParticipationFormStatus.DRAFT));

        mockMvc.perform(put(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(validRequestJson("2026-08-20", "2026-09-30")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.nextAction").value("SUBMIT"))
                .andExpect(jsonPath("$.preferredStartDate").value("2026-08-20"));

        verify(formService).save(
                eq(21L),
                eq(2026),
                argThat(request -> request.experienceCount() == 3
                        && request.availableWorkTypes().equals(List.of("수확", "선별")))
        );
    }

    @Test
    void submitsCompositeFormUsingDedicatedEndpoint() throws Exception {
        when(jwtDecoder.decode("urban-jwt")).thenReturn(jwt("21", "URBAN_FARMER"));
        when(formService.submit(eq(21L), eq(2026), org.mockito.ArgumentMatchers.any()))
                .thenReturn(response(
                        ParticipationFormResponse.ParticipationFormStatus.SUBMITTED
                ));

        mockMvc.perform(post(ENDPOINT + "/submit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(validRequestJson("2026-08-20", "2026-09-30")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.nextAction").value("SAVE_PENDING_CHANGES"));
    }

    @Test
    void reversedPreferredPeriodIsRejectedBeforeServiceCall() throws Exception {
        when(jwtDecoder.decode("urban-jwt")).thenReturn(jwt("21", "URBAN_FARMER"));

        mockMvc.perform(put(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(validRequestJson("2026-09-30", "2026-08-20")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(formService);
    }

    @Test
    void nestedWorkTypePatternRejectsCommaBeforeServiceCall() throws Exception {
        when(jwtDecoder.decode("urban-jwt")).thenReturn(jwt("21", "URBAN_FARMER"));
        String requestJson = validRequestJson("2026-08-20", "2026-09-30")
                .replace(
                        "\"availableWorkTypes\": [\"수확\", \"선별\"]",
                        "\"availableWorkTypes\": [\"수확,선별\"]"
                );

        mockMvc.perform(put(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(formService);
    }

    @Test
    void farmAccountCannotUseUrbanFarmerForm() throws Exception {
        when(jwtDecoder.decode("farm-jwt")).thenReturn(jwt("31", "FARM"));

        mockMvc.perform(get(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farm-jwt"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(formService);
    }

    private String validRequestJson(String startDate, String endDate) {
        return """
                {
                  "agriculturalBusinessRegistered": true,
                  "experienceCount": 3,
                  "experienceNotes": "감자 수확 경험",
                  "preferredRegions": ["CHUNGJU"],
                  "availableDays": ["MONDAY", "WEDNESDAY"],
                  "availableWorkTypes": ["수확", "선별"],
                  "preferredStartDate": "%s",
                  "preferredEndDate": "%s",
                  "canTravel": true,
                  "workPreferenceNotes": "충주 지역 선호",
                  "applicationNote": "평일 근무 희망",
                  "expectedApplicationVersion": 4,
                  "expectedProfileVersion": 2,
                  "expectedWorkPreferenceVersion": 3
                }
                """.formatted(startDate, endDate);
    }

    private ParticipationFormResponse response(
            ParticipationFormResponse.ParticipationFormStatus status
    ) {
        return new ParticipationFormResponse(
                2026,
                status,
                nextAction(status),
                editableFields(status),
                status == ParticipationFormResponse.ParticipationFormStatus.NOT_STARTED
                        ? null
                        : 100L,
                status == ParticipationFormResponse.ParticipationFormStatus.NOT_STARTED
                        ? null
                        : 4L,
                true,
                "평일 근무 희망",
                null,
                null,
                status == ParticipationFormResponse.ParticipationFormStatus.SUBMITTED
                        ? Instant.parse("2026-08-13T00:00:00Z")
                        : null,
                null,
                null,
                200L,
                2L,
                3,
                "감자 수확 경험",
                300L,
                3L,
                List.of(ChungbukCityCounty.CHUNGJU),
                List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                List.of("수확", "선별"),
                LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 9, 30),
                true,
                "충주 지역 선호"
        );
    }

    private ParticipationFormResponse.NextAction nextAction(
            ParticipationFormResponse.ParticipationFormStatus status
    ) {
        return switch (status) {
            case NOT_STARTED, DRAFT -> ParticipationFormResponse.NextAction.SUBMIT;
            case SUBMITTED -> ParticipationFormResponse.NextAction.SAVE_PENDING_CHANGES;
            case REJECTED -> ParticipationFormResponse.NextAction.RESUBMIT;
            case APPROVED -> ParticipationFormResponse.NextAction.SAVE_APPROVED_PREFERENCES;
            case CANCELLED -> ParticipationFormResponse.NextAction.NONE;
        };
    }

    private List<String> editableFields(
            ParticipationFormResponse.ParticipationFormStatus status
    ) {
        if (status == ParticipationFormResponse.ParticipationFormStatus.CANCELLED) {
            return List.of();
        }
        if (status == ParticipationFormResponse.ParticipationFormStatus.APPROVED) {
            return List.of("experienceCount", "preferredRegions");
        }
        return List.of("agriculturalBusinessRegistered", "experienceCount");
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
