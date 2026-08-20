package chungbuk.cityfarmerplus.education.controller;

import chungbuk.cityfarmerplus.auth.config.SecurityConfig;
import chungbuk.cityfarmerplus.auth.exception.GlobalExceptionHandler;
import chungbuk.cityfarmerplus.education.dto.EducationCourseResponse;
import chungbuk.cityfarmerplus.education.service.EducationCourseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EducationCourseController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class EducationCourseControllerWebTest {

    private static final String ENDPOINT = "/api/education/courses";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EducationCourseService courseService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void activeCoursesArePubliclyAvailableWithoutJwt() throws Exception {
        when(courseService.getActiveCourses()).thenReturn(List.of(courseResponse()));

        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("농업안전 기초"))
                .andExpect(jsonPath("$[0].requiredHours").value(8))
                .andExpect(jsonPath("$[0].mandatory").value(true))
                .andExpect(jsonPath("$[0].externalApplicationUrl")
                        .value("https://education.example.com/basic"));

        verify(courseService).getActiveCourses();
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
}
