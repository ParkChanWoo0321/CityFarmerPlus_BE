package chungbuk.cityfarmerplus.farm.ownership.controller;

import chungbuk.cityfarmerplus.auth.config.SecurityConfig;
import chungbuk.cityfarmerplus.auth.exception.GlobalExceptionHandler;
import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import chungbuk.cityfarmerplus.farm.exception.FarmProfileExceptionHandler;
import chungbuk.cityfarmerplus.farm.ownership.dto.FarmOwnershipDocumentResponse;
import chungbuk.cityfarmerplus.farm.ownership.dto.FarmOwnershipSubmissionResponse;
import chungbuk.cityfarmerplus.farm.ownership.entity.FarmOwnershipSubmission;
import chungbuk.cityfarmerplus.farm.ownership.exception.FarmOwnershipException;
import chungbuk.cityfarmerplus.farm.ownership.service.FarmOwnershipSubmissionService;
import chungbuk.cityfarmerplus.farm.ownership.service.FarmOwnershipQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FarmOwnershipSubmissionController.class)
@Import({
        FarmProfileExceptionHandler.class,
        GlobalExceptionHandler.class,
        SecurityConfig.class
})
class FarmOwnershipSubmissionControllerWebTest {

    private static final String ENDPOINT =
            "/api/farm-profiles/me/ownership-submissions";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FarmOwnershipSubmissionService submissionService;

    @MockitoBean
    private FarmOwnershipQueryService queryService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void farmJwtSubmitsMultipleDocumentsForItsOwnSubject() throws Exception {
        when(jwtDecoder.decode("farm-jwt")).thenReturn(jwt("15", "FARM"));
        when(submissionService.submit(eq(15L), any())).thenReturn(response());
        MockMultipartFile first = document("토지대장.pdf");
        MockMultipartFile second = document("농지원부.pdf");

        mockMvc.perform(multipart(ENDPOINT)
                        .file(first)
                        .file(second)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farm-jwt"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(200))
                .andExpect(jsonPath("$.attemptNumber").value(1))
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.farmProfileStatus").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.documents[0].originalFilename")
                        .value("토지대장.pdf"))
                .andExpect(jsonPath("$.documents[0].storageKey").doesNotExist())
                .andExpect(jsonPath("$.documents[0].sha256").doesNotExist());

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<MultipartFile>> documentsCaptor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(submissionService).submit(eq(15L), documentsCaptor.capture());
        assertThat(documentsCaptor.getValue())
                .extracting(MultipartFile::getOriginalFilename)
                .containsExactly("토지대장.pdf", "농지원부.pdf");
    }

    @Test
    void farmJwtGetsItsSubmissionHistory() throws Exception {
        when(jwtDecoder.decode("farm-jwt")).thenReturn(jwt("15", "FARM"));
        when(queryService.getMine(15L)).thenReturn(List.of(response()));

        mockMvc.perform(get(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farm-jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].attemptNumber").value(1));

        verify(queryService).getMine(15L);
    }

    @Test
    void missingJwtIsUnauthorized() throws Exception {
        mockMvc.perform(multipart(ENDPOINT).file(document("토지대장.pdf")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(submissionService);
    }

    @Test
    void nonFarmJwtIsForbidden() throws Exception {
        when(jwtDecoder.decode("urban-jwt"))
                .thenReturn(jwt("20", "URBAN_FARMER"));
        when(jwtDecoder.decode("admin-jwt"))
                .thenReturn(jwt("30", "CENTER_ADMIN"));

        mockMvc.perform(multipart(ENDPOINT)
                        .file(document("토지대장.pdf"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(multipart(ENDPOINT)
                        .file(document("토지대장.pdf"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-jwt"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(submissionService);
    }

    @Test
    void nonNumericJwtSubjectIsUnauthorized() throws Exception {
        when(jwtDecoder.decode("invalid-subject-jwt"))
                .thenReturn(jwt("not-a-number", "FARM"));

        mockMvc.perform(multipart(ENDPOINT)
                        .file(document("토지대장.pdf"))
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer invalid-subject-jwt"
                        ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_AUTHENTICATION"));

        verifyNoInteractions(submissionService);
    }

    @Test
    void missingDocumentsUsesOwnershipErrorContract() throws Exception {
        when(jwtDecoder.decode("farm-jwt")).thenReturn(jwt("15", "FARM"));
        when(submissionService.submit(eq(15L), isNull()))
                .thenThrow(FarmOwnershipException.documentsRequired());

        mockMvc.perform(multipart(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farm-jwt"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("OWNERSHIP_DOCUMENTS_REQUIRED"));
    }

    @Test
    void ownershipErrorsKeepTheirHttpStatusAndCode() throws Exception {
        when(jwtDecoder.decode("farm-jwt")).thenReturn(jwt("15", "FARM"));
        MockMultipartFile document = document("토지대장.pdf");

        when(submissionService.submit(eq(15L), any()))
                .thenThrow(FarmOwnershipException.documentTooLarge());
        mockMvc.perform(multipart(ENDPOINT)
                        .file(document)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farm-jwt"))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code")
                        .value("OWNERSHIP_DOCUMENT_TOO_LARGE"));

        when(submissionService.submit(eq(15L), any()))
                .thenThrow(FarmOwnershipException.unsupportedDocumentType());
        mockMvc.perform(multipart(ENDPOINT)
                        .file(document)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farm-jwt"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code")
                        .value("UNSUPPORTED_OWNERSHIP_DOCUMENT_TYPE"));

        when(submissionService.submit(eq(15L), any()))
                .thenThrow(FarmOwnershipException.submissionNotAllowed());
        mockMvc.perform(multipart(ENDPOINT)
                        .file(document)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farm-jwt"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("OWNERSHIP_SUBMISSION_NOT_ALLOWED"));
    }

    @Test
    void maxUploadExceptionUsesCommonErrorContract() throws Exception {
        when(jwtDecoder.decode("farm-jwt")).thenReturn(jwt("15", "FARM"));
        when(submissionService.submit(eq(15L), any()))
                .thenThrow(new MaxUploadSizeExceededException(35L * 1024 * 1024));

        mockMvc.perform(multipart(ENDPOINT)
                        .file(document("토지대장.pdf"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farm-jwt"))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code")
                        .value("UPLOAD_REQUEST_TOO_LARGE"));
    }

    private MockMultipartFile document(String filename) {
        return new MockMultipartFile(
                "documents",
                filename,
                "application/pdf",
                "%PDF-1.7".getBytes()
        );
    }

    private FarmOwnershipSubmissionResponse response() {
        return new FarmOwnershipSubmissionResponse(
                200L,
                1,
                FarmOwnershipSubmission.SubmissionStatus.PENDING_REVIEW,
                FarmProfile.FarmProfileStatus.PENDING_REVIEW,
                Instant.parse("2026-08-04T00:00:00Z"),
                List.of(new FarmOwnershipDocumentResponse(
                        300L,
                        "토지대장.pdf",
                        "application/pdf",
                        1024L
                ))
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
