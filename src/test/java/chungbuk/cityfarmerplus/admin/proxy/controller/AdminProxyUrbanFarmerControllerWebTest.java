package chungbuk.cityfarmerplus.admin.proxy.controller;

import chungbuk.cityfarmerplus.admin.proxy.service.AdminProxyUrbanFarmerService;
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

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminProxyUrbanFarmerController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class AdminProxyUrbanFarmerControllerWebTest {

    private static final String ENDPOINT = "/api/admin/proxy/urban-farmers";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminProxyUrbanFarmerService proxyService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void centerAdminCreatesUrbanFarmerAccountForJwtSubject() throws Exception {
        authorizeAdmin();

        mockMvc.perform(post(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(accountRequestJson()))
                .andExpect(status().isCreated());

        verify(proxyService).createAccount(
                eq(30L),
                argThat(request -> "proxy_urban".equals(request.loginId())
                        && "방문 접수".equals(request.reason()))
        );
    }

    @Test
    void centerAdminRegistersUrbanFarmerProfileForTargetUser() throws Exception {
        authorizeAdmin();

        mockMvc.perform(post(ENDPOINT + "/21/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content("""
                                {
                                  "agriculturalBusinessRegistered":true,
                                  "experienceCount":3,
                                  "notes":"사과 수확 경험",
                                  "reason":"서면 프로필 접수"
                                }
                                """))
                .andExpect(status().isCreated());

        verify(proxyService).registerProfile(
                eq(30L),
                eq(21L),
                argThat(request -> request.agriculturalBusinessRegistered()
                        && request.experienceCount() == 3
                        && "서면 프로필 접수".equals(request.reason()))
        );
    }

    @Test
    void centerAdminRegistersWorkPreferenceForTargetUser() throws Exception {
        authorizeAdmin();

        mockMvc.perform(put(ENDPOINT + "/21/work-preference")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(workPreferenceRequestJson()))
                .andExpect(status().isOk());

        verify(proxyService).registerWorkPreference(
                eq(30L),
                eq(21L),
                argThat(request -> request.preferredRegions().size() == 1
                        && request.availableDays().size() == 2
                        && "상담 접수".equals(request.reason()))
        );
    }

    @Test
    void centerAdminCreatesParticipationApplicationForTargetUser() throws Exception {
        authorizeAdmin();

        mockMvc.perform(post(ENDPOINT + "/21/participation-applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content("""
                                {
                                  "programYear":2026,
                                  "agriculturalBusinessRegistered":true,
                                  "applicationNote":"평일 근무 희망",
                                  "reason":"방문 신청"
                                }
                                """))
                .andExpect(status().isCreated());

        verify(proxyService).createParticipationApplication(
                eq(30L),
                eq(21L),
                argThat(request -> request.programYear() == 2026
                        && "방문 신청".equals(request.reason()))
        );
    }

    @Test
    void centerAdminSubmitsParticipationApplicationForTargetUser() throws Exception {
        authorizeAdmin();

        mockMvc.perform(post(ENDPOINT + "/21/participation-applications/100/submit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content("""
                                {"reason":"서명 신청서 확인 완료"}
                                """))
                .andExpect(status().isOk());

        verify(proxyService).submitParticipationApplication(
                eq(30L),
                eq(21L),
                eq(100L),
                argThat(request -> "서명 신청서 확인 완료".equals(request.reason()))
        );
    }

    @Test
    void centerAdminSubmitsEducationRequestPartWithoutOptionalDocuments() throws Exception {
        authorizeAdmin();
        MockMultipartFile requestPart = jsonPart(
                "request",
                """
                        {
                          "courseId":10,
                          "completionDate":"2026-08-01",
                          "completionHours":16,
                          "reason":"수료증 원본 확인 완료"
                        }
                        """
        );

        mockMvc.perform(multipart(ENDPOINT + "/21/education-submissions")
                        .file(requestPart)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-jwt"))
                .andExpect(status().isCreated());

        verify(proxyService).submitEducationCertification(
                eq(30L),
                eq(21L),
                argThat(request -> request.courseId() == 10L
                        && request.completionHours() == 16
                        && "수료증 원본 확인 완료".equals(request.reason())),
                isNull()
        );
    }

    @Test
    void reversedWorkPreferencePeriodIsRejectedBeforeServiceCall() throws Exception {
        authorizeAdmin();

        mockMvc.perform(put(ENDPOINT + "/21/work-preference")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "preferredRegions":["CHUNGJU"],
                                  "availableDays":["MONDAY"],
                                  "availableWorkTypes":["수확"],
                                  "preferredStartDate":"2026-09-30",
                                  "preferredEndDate":"2026-09-01",
                                  "canTravel":true,
                                  "reason":"상담 접수"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(proxyService);
    }

    @Test
    void missingEducationRequestPartUsesMultipartErrorContract() throws Exception {
        authorizeAdmin();

        mockMvc.perform(multipart(ENDPOINT + "/21/education-submissions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-jwt"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_MULTIPART_PART"));

        verifyNoInteractions(proxyService);
    }

    @Test
    void missingJwtCannotUseUrbanFarmerProxyRoutes() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountRequestJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(proxyService);
    }

    @Test
    void nonAdminCannotUseUrbanFarmerProxyRoutes() throws Exception {
        when(jwtDecoder.decode("urban-jwt")).thenReturn(jwt("21", "URBAN_FARMER"));

        mockMvc.perform(post(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt")
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
                  "loginId":"proxy_urban",
                  "password":"safe-password-1234",
                  "name":"대리 도시농부",
                  "phoneNumber":"010-1234-5678",
                  "birthDate":"1990-01-01",
                  "address":"충청북도 청주시",
                  "reason":"방문 접수"
                }
                """;
    }

    private String workPreferenceRequestJson() {
        return """
                {
                  "preferredRegions":["CHUNGJU"],
                  "availableDays":["MONDAY","TUESDAY"],
                  "availableWorkTypes":["수확"],
                  "preferredStartDate":"2026-09-01",
                  "preferredEndDate":"2026-09-30",
                  "canTravel":true,
                  "notes":"대중교통 이용",
                  "reason":"상담 접수"
                }
                """;
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
