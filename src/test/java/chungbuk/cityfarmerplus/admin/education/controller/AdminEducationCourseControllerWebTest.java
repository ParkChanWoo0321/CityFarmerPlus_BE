package chungbuk.cityfarmerplus.admin.education.controller;

import chungbuk.cityfarmerplus.admin.education.dto.EducationCourseRequest;
import chungbuk.cityfarmerplus.admin.education.service.AdminEducationCourseService;
import chungbuk.cityfarmerplus.auth.config.SecurityConfig;
import chungbuk.cityfarmerplus.auth.exception.GlobalExceptionHandler;
import chungbuk.cityfarmerplus.common.exception.DomainException;
import chungbuk.cityfarmerplus.education.dto.EducationCourseResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminEducationCourseController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class AdminEducationCourseControllerWebTest {

    private static final String ENDPOINT = "/api/admin/education/courses";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminEducationCourseService courseService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void centerAdminCreatesCourse() throws Exception {
        when(jwtDecoder.decode("admin-jwt")).thenReturn(jwt("30", "CENTER_ADMIN"));
        when(courseService.create(any(EducationCourseRequest.class))).thenReturn(courseResponse());

        mockMvc.perform(post(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(validRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("농업안전 기초"));
    }

    @Test
    void updateMissingCourseUsesEducationCourseErrorContract() throws Exception {
        when(jwtDecoder.decode("admin-jwt")).thenReturn(jwt("30", "CENTER_ADMIN"));
        when(courseService.update(eq(1L), any(EducationCourseRequest.class)))
                .thenThrow(new DomainException(
                        HttpStatus.NOT_FOUND,
                        "EDUCATION_COURSE_NOT_FOUND",
                        "교육 과정을 찾을 수 없습니다."
                ));

        mockMvc.perform(patch(ENDPOINT + "/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(validRequestJson()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EDUCATION_COURSE_NOT_FOUND"));
    }

    @Test
    void centerAdminDeactivatesCourse() throws Exception {
        when(jwtDecoder.decode("admin-jwt")).thenReturn(jwt("30", "CENTER_ADMIN"));
        when(courseService.deactivate(1L)).thenReturn(courseResponse());

        mockMvc.perform(post(ENDPOINT + "/1/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void nonCenterAdminJwtIsForbidden() throws Exception {
        when(jwtDecoder.decode("farm-jwt")).thenReturn(jwt("15", "FARM"));

        mockMvc.perform(post(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farm-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private String validRequestJson() {
        return """
                {
                  "title": "농업안전 기초",
                  "description": "작업 전 이수해야 하는 필수 교육",
                  "requiredHours": 8,
                  "externalApplicationUrl": "https://education.example.com/basic",
                  "mandatory": true,
                  "displayOrder": 1
                }
                """;
    }

    private EducationCourseResponse courseResponse() {
        Instant timestamp = Instant.parse("2026-08-01T00:00:00Z");
        return new EducationCourseResponse(
                1L,
                "농업안전 기초",
                "작업 전 이수해야 하는 필수 교육",
                8,
                "https://education.example.com/basic",
                true,
                true,
                1,
                0L,
                timestamp,
                timestamp
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
