package chungbuk.cityfarmerplus.auth.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerWebTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new UnexpectedFailureController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void unexpectedExceptionReturnsSafeJsonErrorResponse() throws Exception {
        mockMvc.perform(get("/test/unexpected-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."))
                .andExpect(jsonPath("$.detail").doesNotExist());
    }

    @Test
    void illegalArgumentReturnsBadRequestWithoutExposingExceptionDetail() throws Exception {
        mockMvc.perform(get("/test/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("요청 값이 올바르지 않습니다."));
    }

    @Test
    void illegalStateReturnsConflictWithoutExposingExceptionDetail() throws Exception {
        mockMvc.perform(get("/test/illegal-state"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_STATE"))
                .andExpect(jsonPath("$.message").value("현재 상태에서는 요청을 처리할 수 없습니다."));
    }

    @Test
    void missingRequestParameterReturnsBadRequestJson() throws Exception {
        mockMvc.perform(get("/test/required-parameter"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("MISSING_REQUEST_PARAMETER"))
                .andExpect(jsonPath("$.message").value("필수 요청 파라미터가 없습니다."));
    }

    @Test
    void missingResourceReturnsNotFoundJson() throws Exception {
        mockMvc.perform(get("/test/missing-resource"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("요청한 리소스를 찾을 수 없습니다."));
    }

    @Test
    void accessDeniedReturnsForbiddenJson() throws Exception {
        mockMvc.perform(get("/test/access-denied"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."));
    }

    @RestController
    private static class UnexpectedFailureController {

        @GetMapping("/test/unexpected-error")
        void fail() throws Exception {
            throw new Exception("데이터베이스 비밀번호가 노출되면 안 됩니다.");
        }

        @GetMapping("/test/illegal-argument")
        void rejectArgument() {
            throw new IllegalArgumentException("내부 검증 상세가 노출되면 안 됩니다.");
        }

        @GetMapping("/test/illegal-state")
        void rejectState() {
            throw new IllegalStateException("내부 상태 상세가 노출되면 안 됩니다.");
        }

        @GetMapping("/test/required-parameter")
        void requireParameter(@RequestParam String value) {
        }

        @GetMapping("/test/missing-resource")
        void failWithMissingResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(
                    HttpMethod.GET,
                    "/test/missing-resource",
                    "내부 리소스 경로가 노출되면 안 됩니다."
            );
        }

        @GetMapping("/test/access-denied")
        void denyAccess() {
            throw new AccessDeniedException("내부 권한 규칙이 노출되면 안 됩니다.");
        }
    }
}
