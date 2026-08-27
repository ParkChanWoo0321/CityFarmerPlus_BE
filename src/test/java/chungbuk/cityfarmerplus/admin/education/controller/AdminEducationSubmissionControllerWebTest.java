package chungbuk.cityfarmerplus.admin.education.controller;

import chungbuk.cityfarmerplus.admin.education.service.AdminEducationSubmissionService;
import chungbuk.cityfarmerplus.auth.config.SecurityConfig;
import chungbuk.cityfarmerplus.auth.exception.GlobalExceptionHandler;
import chungbuk.cityfarmerplus.education.service.EducationDocumentDownloadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminEducationSubmissionController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class AdminEducationSubmissionControllerWebTest {

    private static final String ENDPOINT =
            "/api/admin/education/submissions/20/documents/30";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminEducationSubmissionService submissionService;

    @MockitoBean
    private EducationDocumentDownloadService documentDownloadService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void listUsesStablePageResponseContract() throws Exception {
        when(jwtDecoder.decode("admin-jwt")).thenReturn(jwt("30", "CENTER_ADMIN"));
        when(submissionService.list(any())).thenReturn(new PageImpl<>(
                List.of(),
                PageRequest.of(1, 5),
                11
        ));

        mockMvc.perform(get("/api/admin/education/submissions")
                        .queryParam("page", "1")
                        .queryParam("size", "5")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.pageable").doesNotExist())
                .andExpect(jsonPath("$.number").doesNotExist());
    }

    @Test
    void centerAdminDownloadsEducationEvidence() throws Exception {
        when(jwtDecoder.decode("admin-jwt")).thenReturn(jwt("30", "CENTER_ADMIN"));
        when(documentDownloadService.downloadForAdmin(20L, 30L)).thenReturn(
                new EducationDocumentDownloadService.DownloadedEducationDocument(
                        new ByteArrayResource("proof".getBytes()),
                        "이수증.pdf",
                        "application/pdf",
                        5L
                )
        );

        mockMvc.perform(get(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-jwt"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("attachment")
                ))
                .andExpect(content().bytes("proof".getBytes()));
    }

    @Test
    void nonCenterAdminCannotDownloadEducationEvidence() throws Exception {
        when(jwtDecoder.decode("farm-jwt")).thenReturn(jwt("15", "FARM"));

        mockMvc.perform(get(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farm-jwt"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(documentDownloadService);
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
