package chungbuk.cityfarmerplus.jobposting.controller;

import chungbuk.cityfarmerplus.auth.config.SecurityConfig;
import chungbuk.cityfarmerplus.auth.exception.GlobalExceptionHandler;
import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import chungbuk.cityfarmerplus.common.web.PageResponse;
import chungbuk.cityfarmerplus.farm.exception.FarmProfileException;
import chungbuk.cityfarmerplus.farm.exception.FarmProfileExceptionHandler;
import chungbuk.cityfarmerplus.jobposting.dto.FarmJobPostingDisplayStatus;
import chungbuk.cityfarmerplus.jobposting.dto.JobPostingResponse;
import chungbuk.cityfarmerplus.jobposting.dto.JobPostingUpsertRequest;
import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;
import chungbuk.cityfarmerplus.jobposting.entity.JobPostingReview;
import chungbuk.cityfarmerplus.jobposting.service.FarmJobPostingService;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FarmJobPostingController.class)
@Import({
        FarmProfileExceptionHandler.class,
        GlobalExceptionHandler.class,
        SecurityConfig.class
})
class FarmJobPostingControllerWebTest {

    private static final String ENDPOINT = "/api/farm/job-postings";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FarmJobPostingService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void farmCreatesPostingAndSubmitsItForReviewInOneRequest() throws Exception {
        when(jwtDecoder.decode("farm-jwt")).thenReturn(jwt("15", "FARM"));
        when(service.create(
                eq(15L),
                any(JobPostingUpsertRequest.class),
                eq(true)
        )).thenReturn(pendingResponse());

        mockMvc.perform(post(ENDPOINT)
                        .queryParam("submitForReview", "true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farm-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(validRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.displayStatus").value("PENDING"));

        verify(service).create(
                eq(15L),
                any(JobPostingUpsertRequest.class),
                eq(true)
        );
    }

    @Test
    void farmReadsRejectedPostingsUsingDisplayStatusFilter() throws Exception {
        when(jwtDecoder.decode("farm-jwt")).thenReturn(jwt("15", "FARM"));
        when(service.getMine(
                15L,
                FarmJobPostingDisplayStatus.REJECTED,
                1,
                10
        )).thenReturn(new PageResponse<>(
                List.of(rejectedResponse()),
                1,
                10,
                11,
                2,
                false
        ));

        mockMvc.perform(get(ENDPOINT)
                        .queryParam("displayStatus", "REJECTED")
                        .queryParam("page", "1")
                        .queryParam("size", "10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farm-jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.content[0].displayStatus").value("REJECTED"))
                .andExpect(jsonPath("$.content[0].latestReviewAction").value("REJECTED"))
                .andExpect(jsonPath("$.content[0].latestReviewReason")
                        .value("집결 장소를 구체적으로 입력해 주세요."))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10));

        verify(service).getMine(
                15L,
                FarmJobPostingDisplayStatus.REJECTED,
                1,
                10
        );
    }

    @Test
    void invalidDisplayStatusIsRejectedBeforeServiceCall() throws Exception {
        when(jwtDecoder.decode("farm-jwt")).thenReturn(jwt("15", "FARM"));

        mockMvc.perform(get(ENDPOINT)
                        .queryParam("displayStatus", "OPEN")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farm-jwt"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"));

        verifyNoInteractions(service);
    }

    @Test
    void missingFarmProfileUsesFarmProfileErrorContract() throws Exception {
        when(jwtDecoder.decode("farm-jwt")).thenReturn(jwt("15", "FARM"));
        when(service.getMine(15L, null, 0, 20))
                .thenThrow(FarmProfileException.profileNotFound());

        mockMvc.perform(get(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farm-jwt"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FARM_PROFILE_NOT_FOUND"));
    }

    private String validRequestJson() {
        return """
                {
                  "crop": "감자",
                  "workType": "수확",
                  "workDate": "2099-08-20",
                  "startTime": "09:00:00",
                  "endTime": "16:00:00",
                  "capacity": 3,
                  "meetingPlace": "농장 입구",
                  "wageAmount": 100000,
                  "wageUnit": "DAILY",
                  "supplies": "장갑, 모자",
                  "precautions": "농기계 주변 안전거리 유지",
                  "farmMessage": "함께 일해요",
                  "applicantPreference": "초보자 환영",
                  "title": "감자 수확 도우미를 찾아요",
                  "description": "감자 수확 작업자를 모집합니다.",
                  "beginnerGuide": "농가 안내에 따라 작업해 주세요."
                }
                """;
    }

    private JobPostingResponse pendingResponse() {
        return response(
                JobPosting.JobPostingStatus.PENDING_REVIEW,
                FarmJobPostingDisplayStatus.PENDING,
                null,
                null
        );
    }

    private JobPostingResponse rejectedResponse() {
        return response(
                JobPosting.JobPostingStatus.DRAFT,
                FarmJobPostingDisplayStatus.REJECTED,
                JobPostingReview.ReviewAction.REJECTED,
                "집결 장소를 구체적으로 입력해 주세요."
        );
    }

    private JobPostingResponse response(
            JobPosting.JobPostingStatus status,
            FarmJobPostingDisplayStatus displayStatus,
            JobPostingReview.ReviewAction latestReviewAction,
            String latestReviewReason
    ) {
        Instant now = Instant.parse("2026-08-11T01:00:00Z");
        return new JobPostingResponse(
                100L,
                10L,
                "충주 감자농가",
                ChungbukCityCounty.CHUNGJU,
                "충청북도 충주시 예시로 1",
                "01012345678",
                "감자",
                "수확",
                LocalDate.of(2099, 8, 20),
                LocalTime.of(9, 0),
                LocalTime.of(16, 0),
                3,
                "농장 입구",
                100_000,
                JobPosting.WageUnit.DAILY,
                "장갑, 모자",
                "농기계 주변 안전거리 유지",
                "함께 일해요",
                "초보자 환영",
                "감자 수확 도우미를 찾아요",
                "감자 수확 작업자를 모집합니다.",
                "농가 안내에 따라 작업해 주세요.",
                status,
                displayStatus,
                now,
                null,
                null,
                null,
                now,
                now,
                latestReviewAction,
                latestReviewReason,
                latestReviewAction == null ? null : now
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
