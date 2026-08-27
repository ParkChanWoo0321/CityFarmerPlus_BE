package chungbuk.cityfarmerplus.admin.proxy.controller;

import chungbuk.cityfarmerplus.admin.proxy.dto.ProxyAccountRequest;
import chungbuk.cityfarmerplus.admin.proxy.dto.ProxyFarmOwnershipSubmissionRequest;
import chungbuk.cityfarmerplus.admin.proxy.dto.ProxyFarmProfileRequest;
import chungbuk.cityfarmerplus.admin.proxy.dto.ProxyJobPostingDraftRequest;
import chungbuk.cityfarmerplus.admin.proxy.service.AdminProxyFarmService;
import chungbuk.cityfarmerplus.auth.config.SecurityConfig;
import chungbuk.cityfarmerplus.auth.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

@WebMvcTest(AdminProxyFarmController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class AdminProxyFarmControllerWebTest {

    private static final String ENDPOINT = "/api/admin/proxy/farms";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminProxyFarmService proxyService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void centerAdminCreatesFarmAccountForJwtSubject() throws Exception {
        authorizeAdmin();

        mockMvc.perform(post(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(accountRequestJson()))
                .andExpect(status().isCreated());

        verify(proxyService).createAccount(
                eq(30L),
                argThat(request -> "proxy_farm".equals(request.loginId())
                        && "방문 접수".equals(request.reason()))
        );
    }

    @Test
    void centerAdminRegistersFarmProfileForTargetUser() throws Exception {
        authorizeAdmin();

        mockMvc.perform(post(ENDPOINT + "/15/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(profileRequestJson()))
                .andExpect(status().isCreated());

        verify(proxyService).registerProfile(
                eq(30L),
                eq(15L),
                argThat(request -> "충주 사과농원".equals(request.farmName())
                        && request.farmAreaPyeong() == 1200
                        && "서면 신청서 접수".equals(request.reason()))
        );
    }

    @Test
    void centerAdminSubmitsOwnershipRequestPartWithoutOptionalDocuments() throws Exception {
        authorizeAdmin();
        MockMultipartFile requestPart = jsonPart(
                "request",
                """
                        {"reason":"원본 서류 확인 완료"}
                        """
        );

        mockMvc.perform(multipart(ENDPOINT + "/15/ownership-submissions")
                        .file(requestPart)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-jwt"))
                .andExpect(status().isCreated());

        verify(proxyService).submitOwnershipDocuments(
                eq(30L),
                eq(15L),
                argThat(request -> "원본 서류 확인 완료".equals(request.reason())),
                isNull()
        );
    }

    @Test
    void centerAdminCreatesJobPostingAndBindsSubmitForReviewFlag() throws Exception {
        authorizeAdmin();

        mockMvc.perform(post(ENDPOINT + "/15/job-postings")
                        .queryParam("submitForReview", "true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(jobPostingRequestJson()))
                .andExpect(status().isCreated());

        verify(proxyService).createJobPostingDraft(
                eq(30L),
                eq(15L),
                argThat(request -> "사과 수확 작업".equals(request.title())
                        && request.capacity() == 5
                        && "전화 접수".equals(request.reason())),
                eq(true)
        );
    }

    @Test
    void invalidAccountRequestIsRejectedBeforeServiceCall() throws Exception {
        authorizeAdmin();

        mockMvc.perform(post(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId":"BAD ID",
                                  "password":"short",
                                  "name":"",
                                  "reason":""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(proxyService);
    }

    @Test
    void missingOwnershipRequestPartUsesMultipartErrorContract() throws Exception {
        authorizeAdmin();

        mockMvc.perform(multipart(ENDPOINT + "/15/ownership-submissions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-jwt"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_MULTIPART_PART"));

        verifyNoInteractions(proxyService);
    }

    @Test
    void missingJwtCannotUseFarmProxyRoutes() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountRequestJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(proxyService);
    }

    @Test
    void nonAdminCannotUseFarmProxyRoutes() throws Exception {
        when(jwtDecoder.decode("farm-jwt")).thenReturn(jwt("15", "FARM"));

        mockMvc.perform(post(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farm-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountRequestJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(proxyService);
    }

    private void authorizeAdmin() {
        when(jwtDecoder.decode("admin-jwt")).thenReturn(jwt("30", "CENTER_ADMIN"));
    }

    private String accountRequestJson() {
        return """
                {
                  "loginId":"proxy_farm",
                  "password":"safe-password-1234",
                  "name":"대리 농가",
                  "phoneNumber":"010-1234-5678",
                  "birthDate":"1980-01-01",
                  "address":"충청북도 충주시",
                  "reason":"방문 접수"
                }
                """;
    }

    private String profileRequestJson() {
        return """
                {
                  "farmName":"충주 사과농원",
                  "representativeName":"홍길동",
                  "contactNumber":"010-1234-5678",
                  "farmAddress":"충청북도 충주시 예시로 1",
                  "cityCounty":"CHUNGJU",
                  "crops":["사과"],
                  "mainActivities":"사과 재배와 수확",
                  "businessRegistrationNumber":"123-45-67890",
                  "farmAreaPyeong":1200,
                  "reason":"서면 신청서 접수"
                }
                """;
    }

    private String jobPostingRequestJson() {
        return """
                {
                  "crop":"사과",
                  "workType":"수확",
                  "workDate":"%s",
                  "startTime":"09:00:00",
                  "endTime":"17:00:00",
                  "capacity":5,
                  "meetingPlace":"충주 사과농원 입구",
                  "wageAmount":120000,
                  "wageUnit":"DAILY",
                  "supplies":"장갑",
                  "precautions":"안전화 착용",
                  "farmMessage":"안전하게 작업합니다.",
                  "applicantPreference":"초보 가능",
                  "title":"사과 수확 작업",
                  "description":"사과 수확 인력을 모집합니다.",
                  "beginnerGuide":"현장에서 안내합니다.",
                  "reason":"전화 접수"
                }
                """.formatted(LocalDate.now().plusDays(1));
    }

    private MockMultipartFile jsonPart(String name, String json) {
        return new MockMultipartFile(
                name,
                "",
                MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8)
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
