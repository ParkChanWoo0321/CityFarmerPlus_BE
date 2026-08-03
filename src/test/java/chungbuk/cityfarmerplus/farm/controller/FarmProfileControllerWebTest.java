package chungbuk.cityfarmerplus.farm.controller;

import chungbuk.cityfarmerplus.auth.config.SecurityConfig;
import chungbuk.cityfarmerplus.auth.exception.GlobalExceptionHandler;
import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import chungbuk.cityfarmerplus.farm.dto.FarmProfileCreateRequest;
import chungbuk.cityfarmerplus.farm.dto.FarmProfileResponse;
import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import chungbuk.cityfarmerplus.farm.exception.FarmProfileException;
import chungbuk.cityfarmerplus.farm.exception.FarmProfileExceptionHandler;
import chungbuk.cityfarmerplus.farm.service.FarmProfileService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FarmProfileController.class)
@Import({
        FarmProfileExceptionHandler.class,
        GlobalExceptionHandler.class,
        SecurityConfig.class
})
class FarmProfileControllerWebTest {

    private static final String CREATE_ENDPOINT = "/api/farm-profiles";
    private static final String MY_PROFILE_ENDPOINT = "/api/farm-profiles/me";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FarmProfileService farmProfileService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void farmJwtCreatesProfileForItsOwnSubject() throws Exception {
        when(jwtDecoder.decode("farm-jwt")).thenReturn(jwt("15", "FARM"));
        when(farmProfileService.create(eq(15L), any(FarmProfileCreateRequest.class)))
                .thenReturn(response());

        mockMvc.perform(post(CREATE_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farm-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(validRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, MY_PROFILE_ENDPOINT))
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.cityCounty").value("CHUNGJU"))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        verify(farmProfileService).create(eq(15L), any(FarmProfileCreateRequest.class));
    }

    @Test
    void farmJwtGetsItsOwnProfile() throws Exception {
        when(jwtDecoder.decode("farm-jwt")).thenReturn(jwt("15", "FARM"));
        when(farmProfileService.getMine(15L)).thenReturn(response());

        mockMvc.perform(get(MY_PROFILE_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farm-jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farmName").value("충주 사과농원"));

        verify(farmProfileService).getMine(15L);
    }

    @Test
    void missingJwtIsUnauthorized() throws Exception {
        mockMvc.perform(post(CREATE_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get(MY_PROFILE_ENDPOINT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(farmProfileService);
    }

    @Test
    void nonFarmJwtIsForbidden() throws Exception {
        when(jwtDecoder.decode("urban-farmer-jwt"))
                .thenReturn(jwt("20", "URBAN_FARMER"));
        when(jwtDecoder.decode("admin-jwt"))
                .thenReturn(jwt("30", "CENTER_ADMIN"));

        mockMvc.perform(post(CREATE_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-farmer-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get(MY_PROFILE_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-jwt"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(farmProfileService);
    }

    @Test
    void farmJwtWithNonNumericSubjectIsUnauthorized() throws Exception {
        when(jwtDecoder.decode("invalid-subject-jwt"))
                .thenReturn(jwt("not-a-number", "FARM"));

        mockMvc.perform(get(MY_PROFILE_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-subject-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_AUTHENTICATION"));

        verifyNoInteractions(farmProfileService);
    }

    @Test
    void invalidRequestIsRejectedBeforeServiceCall() throws Exception {
        when(jwtDecoder.decode("farm-jwt")).thenReturn(jwt("15", "FARM"));

        mockMvc.perform(post(CREATE_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farm-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content("""
                                {
                                  "farmName": "",
                                  "representativeName": "홍길동",
                                  "contactNumber": "invalid",
                                  "farmAddress": "",
                                  "cityCounty": "CHUNGJU",
                                  "crops": [],
                                  "mainActivities": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(farmProfileService, never()).create(any(), any());
    }

    @Test
    void unknownCityCountyIsAnInvalidRequest() throws Exception {
        when(jwtDecoder.decode("farm-jwt")).thenReturn(jwt("15", "FARM"));

        mockMvc.perform(post(CREATE_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farm-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(validRequestJson().replace("CHUNGJU", "SEOUL")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(farmProfileService, never()).create(any(), any());
    }

    @Test
    void profileConflictUsesFarmProfileErrorContract() throws Exception {
        when(jwtDecoder.decode("farm-jwt")).thenReturn(jwt("15", "FARM"));
        when(farmProfileService.create(eq(15L), any(FarmProfileCreateRequest.class)))
                .thenThrow(FarmProfileException.profileAlreadyExists());

        mockMvc.perform(post(CREATE_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farm-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(validRequestJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FARM_PROFILE_ALREADY_EXISTS"));
    }

    @Test
    void missingProfileUsesFarmProfileErrorContract() throws Exception {
        when(jwtDecoder.decode("farm-jwt")).thenReturn(jwt("15", "FARM"));
        when(farmProfileService.getMine(15L))
                .thenThrow(FarmProfileException.profileNotFound());

        mockMvc.perform(get(MY_PROFILE_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farm-jwt"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FARM_PROFILE_NOT_FOUND"));
    }

    private String validRequestJson() {
        return """
                {
                  "farmName": "충주 사과농원",
                  "representativeName": "홍길동",
                  "contactNumber": "010-1234-5678",
                  "farmAddress": "충청북도 충주시 예시로 1",
                  "cityCounty": "CHUNGJU",
                  "crops": ["사과", "복숭아"],
                  "mainActivities": "사과 재배와 수확 작업을 합니다.",
                  "businessRegistrationNumber": "123-45-67890"
                }
                """;
    }

    private FarmProfileResponse response() {
        Instant now = Instant.parse("2026-08-03T00:00:00Z");
        return new FarmProfileResponse(
                100L,
                "충주 사과농원",
                "홍길동",
                "01012345678",
                "충청북도 충주시 예시로 1",
                ChungbukCityCounty.CHUNGJU,
                List.of("사과", "복숭아"),
                "사과 재배와 수확 작업을 합니다.",
                "1234567890",
                FarmProfile.FarmProfileStatus.DRAFT,
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
