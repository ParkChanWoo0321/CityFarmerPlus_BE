package chungbuk.cityfarmerplus.education.controller;

import chungbuk.cityfarmerplus.auth.config.SecurityConfig;
import chungbuk.cityfarmerplus.auth.exception.GlobalExceptionHandler;
import chungbuk.cityfarmerplus.education.dto.EducationCertificationResponse;
import chungbuk.cityfarmerplus.education.dto.EducationCourseProgressResponse;
import chungbuk.cityfarmerplus.education.dto.EducationDocumentResponse;
import chungbuk.cityfarmerplus.education.dto.EducationSubmissionResponse;
import chungbuk.cityfarmerplus.education.entity.EducationCertificateSubmission;
import chungbuk.cityfarmerplus.education.entity.EducationCertification;
import chungbuk.cityfarmerplus.education.progress.entity.EducationEnrollment;
import chungbuk.cityfarmerplus.education.service.EducationDocumentDownloadService;
import chungbuk.cityfarmerplus.education.service.EducationSubmissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UrbanFarmerEducationController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class UrbanFarmerEducationControllerWebTest {

    private static final String ENDPOINT =
            "/api/urban-farmers/me/education-certification";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EducationSubmissionService submissionService;

    @MockitoBean
    private EducationDocumentDownloadService downloadService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void urbanFarmerJwtGetsItsCurrentEducationProgress() throws Exception {
        when(jwtDecoder.decode("urban-jwt"))
                .thenReturn(jwt("21", "URBAN_FARMER"));
        when(submissionService.getCurrent(21L)).thenReturn(certificationResponse());

        mockMvc.perform(get(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urbanFarmerId").value(21))
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.eligibleToApply").value(false))
                .andExpect(jsonPath("$.requiredCourseCount").value(1))
                .andExpect(jsonPath("$.courses[0].progressStatus")
                        .value("IN_PROGRESS"))
                .andExpect(jsonPath("$.courses[0].completedMinutes").value(240))
                .andExpect(jsonPath("$.courses[0].remainingMinutes").value(240))
                .andExpect(jsonPath("$.courses[0].progressPercentage").value(50));

        verify(submissionService).getCurrent(21L);
    }

    @Test
    void missingJwtIsUnauthorized() throws Exception {
        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(submissionService, downloadService);
    }

    @Test
    void nonUrbanFarmerJwtIsForbidden() throws Exception {
        when(jwtDecoder.decode("farm-jwt")).thenReturn(jwt("31", "FARM"));

        mockMvc.perform(get(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farm-jwt"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(submissionService, downloadService);
    }

    @Test
    void urbanFarmerSubmitsCertificateAsMultipartRequest() throws Exception {
        when(jwtDecoder.decode("urban-jwt"))
                .thenReturn(jwt("21", "URBAN_FARMER"));
        when(submissionService.submit(
                eq(21L),
                argThat(request -> request.courseId().equals(1L)
                        && request.completionDate().equals(LocalDate.of(2026, 8, 1))
                        && request.completionHours() == 8),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(submissionResponse());

        mockMvc.perform(multipart(ENDPOINT + "/submissions")
                        .file(requestPart(8))
                        .file(documentPart())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.documents[0].originalFilename")
                        .value("교육이수증.pdf"));

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<MultipartFile>> documentsCaptor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(submissionService).submit(
                eq(21L),
                argThat(request -> request.courseId().equals(1L)
                        && request.completionHours() == 8),
                documentsCaptor.capture()
        );
        assertThat(documentsCaptor.getValue())
                .extracting(MultipartFile::getOriginalFilename)
                .containsExactly("교육이수증.pdf");
    }

    @Test
    void invalidMultipartRequestIsRejectedBeforeServiceCall() throws Exception {
        when(jwtDecoder.decode("urban-jwt"))
                .thenReturn(jwt("21", "URBAN_FARMER"));

        mockMvc.perform(multipart(ENDPOINT + "/submissions")
                        .file(requestPart(7))
                        .file(documentPart())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(submissionService);
    }

    @Test
    void urbanFarmerGetsItsSubmissionHistoryAndDetail() throws Exception {
        when(jwtDecoder.decode("urban-jwt"))
                .thenReturn(jwt("21", "URBAN_FARMER"));
        when(submissionService.getHistory(21L))
                .thenReturn(List.of(submissionResponse()));
        when(submissionService.getMine(21L, 100L))
                .thenReturn(submissionResponse());

        mockMvc.perform(get(ENDPOINT + "/submissions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(100))
                .andExpect(jsonPath("$[0].urbanFarmerId").value(21));

        mockMvc.perform(get(ENDPOINT + "/submissions/100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.courseTitle").value("농업안전 기초"))
                .andExpect(jsonPath("$.requiredHoursSnapshot").value(8));

        verify(submissionService).getHistory(21L);
        verify(submissionService).getMine(21L, 100L);
    }

    @Test
    void urbanFarmerDownloadsOwnedDocumentWithAttachmentHeaders() throws Exception {
        byte[] bytes = "certificate".getBytes(StandardCharsets.UTF_8);
        when(jwtDecoder.decode("urban-jwt"))
                .thenReturn(jwt("21", "URBAN_FARMER"));
        when(downloadService.downloadMine(21L, 100L, 200L)).thenReturn(
                new EducationDocumentDownloadService.DownloadedEducationDocument(
                        new ByteArrayResource(bytes),
                        "교육이수증.pdf",
                        MediaType.APPLICATION_PDF_VALUE,
                        bytes.length
                )
        );

        mockMvc.perform(get(ENDPOINT + "/submissions/100/documents/200")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(bytes))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_PDF_VALUE
                ))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_LENGTH,
                        String.valueOf(bytes.length)
                ))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        containsString("attachment")
                ));

        verify(downloadService).downloadMine(21L, 100L, 200L);
    }

    private MockMultipartFile requestPart(int completionHours) {
        String request = """
                {
                  "courseId": 1,
                  "completionDate": "2026-08-01",
                  "completionHours": %d
                }
                """.formatted(completionHours);
        return new MockMultipartFile(
                "request",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                request.getBytes(StandardCharsets.UTF_8)
        );
    }

    private MockMultipartFile documentPart() {
        return new MockMultipartFile(
                "documents",
                "교육이수증.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "%PDF-1.7 certificate".getBytes(StandardCharsets.US_ASCII)
        );
    }

    private EducationCertificationResponse certificationResponse() {
        Instant timestamp = Instant.parse("2026-08-02T00:00:00Z");
        return new EducationCertificationResponse(
                50L,
                21L,
                EducationCertification.CertificationStatus.PENDING_REVIEW,
                null,
                null,
                null,
                0L,
                timestamp,
                timestamp,
                false,
                1L,
                0L,
                List.of(new EducationCourseProgressResponse(
                        1L,
                        "농업안전 기초",
                        "필수 교육",
                        8,
                        "https://education.example.com/basic",
                        true,
                        EducationCertificateSubmission.SubmissionStatus.PENDING_REVIEW,
                        100L,
                        1,
                        null,
                        null,
                        timestamp,
                        EducationEnrollment.ProgressStatus.IN_PROGRESS,
                        480,
                        240,
                        240,
                        50,
                        timestamp,
                        null,
                        timestamp,
                        timestamp
                ))
        );
    }

    private EducationSubmissionResponse submissionResponse() {
        Instant submittedAt = Instant.parse("2026-08-02T00:00:00Z");
        return new EducationSubmissionResponse(
                100L,
                50L,
                21L,
                "도시농부",
                1L,
                "농업안전 기초",
                8,
                1,
                LocalDate.of(2026, 8, 1),
                8,
                EducationCertificateSubmission.SubmissionStatus.PENDING_REVIEW,
                null,
                null,
                null,
                null,
                List.of(new EducationDocumentResponse(
                        200L,
                        0,
                        "교육이수증.pdf",
                        MediaType.APPLICATION_PDF_VALUE,
                        1024L,
                        "a".repeat(64),
                        submittedAt
                )),
                0L,
                submittedAt
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
