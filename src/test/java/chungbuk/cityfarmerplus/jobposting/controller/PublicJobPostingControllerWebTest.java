package chungbuk.cityfarmerplus.jobposting.controller;

import chungbuk.cityfarmerplus.auth.config.SecurityConfig;
import chungbuk.cityfarmerplus.auth.exception.GlobalExceptionHandler;
import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import chungbuk.cityfarmerplus.common.web.PageResponse;
import chungbuk.cityfarmerplus.jobposting.dto.PublicJobPostingResponse;
import chungbuk.cityfarmerplus.jobposting.dto.PublicRecruitmentStatus;
import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;
import chungbuk.cityfarmerplus.jobposting.service.PublicJobPostingService;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicJobPostingController.class)
@Import({
        GlobalExceptionHandler.class,
        SecurityConfig.class
})
class PublicJobPostingControllerWebTest {

    private static final String ENDPOINT = "/api/job-postings";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicJobPostingService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void authenticatedUserSearchesPostingsWithExtendedFilters() throws Exception {
        when(jwtDecoder.decode("urban-jwt")).thenReturn(jwt("20", "URBAN_FARMER"));
        when(service.getPostings(
                20L,
                "potato",
                ChungbukCityCounty.CHUNGJU,
                "potato",
                LocalDate.of(2099, 8, 1),
                LocalDate.of(2099, 8, 31),
                "harvest",
                PublicRecruitmentStatus.ALL,
                1,
                10
        )).thenReturn(new PageResponse<>(
                List.of(response()),
                1,
                10,
                11,
                2,
                false
        ));

        mockMvc.perform(get(ENDPOINT)
                        .queryParam("keyword", "potato")
                        .queryParam("region", "CHUNGJU")
                        .queryParam("crop", "potato")
                        .queryParam("dateFrom", "2099-08-01")
                        .queryParam("dateTo", "2099-08-31")
                        .queryParam("workType", "harvest")
                        .queryParam("recruitmentStatus", "ALL")
                        .queryParam("page", "1")
                        .queryParam("size", "10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(100))
                .andExpect(jsonPath("$.content[0].farmName").value("Chungju farm"))
                .andExpect(jsonPath("$.content[0].recruitmentStatus").value("OPEN"))
                .andExpect(jsonPath("$.content[0].acceptingApplications").value(true))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10));

        verify(service).getPostings(
                20L,
                "potato",
                ChungbukCityCounty.CHUNGJU,
                "potato",
                LocalDate.of(2099, 8, 1),
                LocalDate.of(2099, 8, 31),
                "harvest",
                PublicRecruitmentStatus.ALL,
                1,
                10
        );
    }

    @Test
    void omittedRecruitmentStatusDefaultsToOpen() throws Exception {
        when(jwtDecoder.decode("urban-jwt")).thenReturn(jwt("20", "URBAN_FARMER"));
        when(service.getPostings(
                20L,
                "",
                null,
                null,
                null,
                null,
                null,
                PublicRecruitmentStatus.OPEN,
                0,
                20
        )).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, false));

        mockMvc.perform(get(ENDPOINT)
                        .queryParam("keyword", "")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));

        verify(service).getPostings(
                20L,
                "",
                null,
                null,
                null,
                null,
                null,
                PublicRecruitmentStatus.OPEN,
                0,
                20
        );
    }

    @Test
    void readsClosedPostingWhenExplicitlyIncluded() throws Exception {
        when(jwtDecoder.decode("urban-jwt")).thenReturn(jwt("20", "URBAN_FARMER"));
        when(service.getPosting(20L, 100L, true)).thenReturn(response());

        mockMvc.perform(get(ENDPOINT + "/100")
                        .queryParam("includeClosed", "true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100));

        verify(service).getPosting(20L, 100L, true);
    }

    @Test
    void keywordLongerThanOneHundredCharactersIsRejectedBeforeServiceCall()
            throws Exception {
        when(jwtDecoder.decode("urban-jwt")).thenReturn(jwt("20", "URBAN_FARMER"));

        mockMvc.perform(get(ENDPOINT)
                        .queryParam("keyword", "a".repeat(101))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(service);
    }

    @Test
    void searchRequiresAuthentication() throws Exception {
        mockMvc.perform(get(ENDPOINT).queryParam("keyword", "potato"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(service);
    }

    @Test
    void detailRequiresAuthentication() throws Exception {
        mockMvc.perform(get(ENDPOINT + "/100"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(service);
    }

    private PublicJobPostingResponse response() {
        return new PublicJobPostingResponse(
                100L,
                10L,
                "Chungju farm",
                ChungbukCityCounty.CHUNGJU,
                "potato",
                "harvest",
                LocalDate.of(2099, 8, 20),
                LocalTime.of(9, 0),
                LocalTime.of(16, 0),
                3,
                "farm entrance",
                100_000,
                JobPosting.WageUnit.DAILY,
                "gloves, hat",
                "keep a safe distance",
                "welcome",
                "beginners welcome",
                "Looking for potato harvest helpers",
                "We are recruiting potato harvest helpers.",
                "Follow the farmer's instructions.",
                Instant.parse("2026-08-11T01:00:00Z"),
                PublicRecruitmentStatus.OPEN,
                true,
                null
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
