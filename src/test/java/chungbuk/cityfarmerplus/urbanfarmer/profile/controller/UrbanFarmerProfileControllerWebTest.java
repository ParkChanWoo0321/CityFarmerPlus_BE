package chungbuk.cityfarmerplus.urbanfarmer.profile.controller;

import chungbuk.cityfarmerplus.auth.config.SecurityConfig;
import chungbuk.cityfarmerplus.auth.exception.GlobalExceptionHandler;
import chungbuk.cityfarmerplus.urbanfarmer.profile.dto.UrbanFarmerProfileRequest;
import chungbuk.cityfarmerplus.urbanfarmer.profile.dto.UrbanFarmerProfileResponse;
import chungbuk.cityfarmerplus.urbanfarmer.profile.service.UrbanFarmerProfileService;
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

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UrbanFarmerProfileController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class UrbanFarmerProfileControllerWebTest {

    private static final String ENDPOINT = "/api/urban-farmers/me/profile";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UrbanFarmerProfileService profileService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void urbanFarmerCreatesOwnProfileWithJwtSubject() throws Exception {
        authorizeUrbanFarmer();
        when(profileService.create(
                eq(21L),
                argThat(request -> request.agriculturalBusinessRegistered()
                        && request.experienceCount() == 3
                        && "사과 수확 경험".equals(request.notes()))
        )).thenReturn(response(0L));

        mockMvc.perform(post(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(validRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.userId").value(21))
                .andExpect(jsonPath("$.agriculturalBusinessRegistered").value(true))
                .andExpect(jsonPath("$.experienceCount").value(3));

        verify(profileService).create(eq(21L), argThat(
                request -> request.experienceCount() == 3
        ));
    }

    @Test
    void urbanFarmerGetsOwnProfile() throws Exception {
        authorizeUrbanFarmer();
        when(profileService.getMine(21L)).thenReturn(response(0L));

        mockMvc.perform(get(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.notes").value("사과 수확 경험"));

        verify(profileService).getMine(21L);
    }

    @Test
    void urbanFarmerUpdatesOwnProfile() throws Exception {
        authorizeUrbanFarmer();
        when(profileService.update(eq(21L), argThat(
                request -> request.experienceCount() == 3
        ))).thenReturn(response(1L));

        mockMvc.perform(patch(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(validRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        verify(profileService).update(eq(21L), argThat(
                request -> request.agriculturalBusinessRegistered()
        ));
    }

    @Test
    void invalidProfileIsRejectedBeforeServiceCall() throws Exception {
        authorizeUrbanFarmer();

        mockMvc.perform(post(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agriculturalBusinessRegistered": null,
                                  "experienceCount": -1,
                                  "notes": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(profileService);
    }

    @Test
    void missingJwtCannotAccessProfile() throws Exception {
        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(profileService);
    }

    @Test
    void nonUrbanFarmerCannotAccessProfile() throws Exception {
        when(jwtDecoder.decode("farm-jwt")).thenReturn(jwt("15", "FARM"));

        mockMvc.perform(get(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farm-jwt"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(profileService);
    }

    private void authorizeUrbanFarmer() {
        when(jwtDecoder.decode("urban-jwt")).thenReturn(jwt("21", "URBAN_FARMER"));
    }

    private String validRequestJson() {
        return """
                {
                  "agriculturalBusinessRegistered": true,
                  "experienceCount": 3,
                  "notes": "사과 수확 경험"
                }
                """;
    }

    private UrbanFarmerProfileResponse response(long version) {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        return new UrbanFarmerProfileResponse(
                100L,
                21L,
                true,
                3,
                "사과 수확 경험",
                version,
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
