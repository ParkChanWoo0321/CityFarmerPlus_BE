package chungbuk.cityfarmerplus.education.controller;

import chungbuk.cityfarmerplus.common.web.AuthenticatedUser;
import chungbuk.cityfarmerplus.education.dto.EducationCertificationResponse;
import chungbuk.cityfarmerplus.education.dto.EducationSubmissionRequest;
import chungbuk.cityfarmerplus.education.dto.EducationSubmissionResponse;
import chungbuk.cityfarmerplus.education.service.EducationDocumentDownloadService;
import chungbuk.cityfarmerplus.education.service.EducationSubmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/urban-farmers/me/education-certification")
@RequiredArgsConstructor
@PreAuthorize("hasRole('URBAN_FARMER')")
public class UrbanFarmerEducationController {

    private final EducationSubmissionService submissionService;
    private final EducationDocumentDownloadService downloadService;

    @GetMapping
    public ResponseEntity<EducationCertificationResponse> getCurrent(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                submissionService.getCurrent(AuthenticatedUser.id(authentication))
        );
    }

    @GetMapping("/submissions")
    public ResponseEntity<List<EducationSubmissionResponse>> getHistory(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                submissionService.getHistory(AuthenticatedUser.id(authentication))
        );
    }

    @PostMapping(
            path = "/submissions",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<EducationSubmissionResponse> submit(
            Authentication authentication,
            @Valid @RequestPart("request") EducationSubmissionRequest request,
            @RequestPart(name = "documents", required = false)
            List<MultipartFile> documents
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(submissionService.submit(
                AuthenticatedUser.id(authentication),
                request,
                documents
        ));
    }

    @GetMapping("/submissions/{submissionId}")
    public ResponseEntity<EducationSubmissionResponse> getSubmission(
            Authentication authentication,
            @PathVariable Long submissionId
    ) {
        return ResponseEntity.ok(submissionService.getMine(
                AuthenticatedUser.id(authentication),
                submissionId
        ));
    }

    @GetMapping("/submissions/{submissionId}/documents/{documentId}")
    public ResponseEntity<Resource> download(
            Authentication authentication,
            @PathVariable Long submissionId,
            @PathVariable Long documentId
    ) {
        EducationDocumentDownloadService.DownloadedEducationDocument document =
                downloadService.downloadMine(
                        AuthenticatedUser.id(authentication),
                        submissionId,
                        documentId
                );
        return downloadResponse(document);
    }

    private ResponseEntity<Resource> downloadResponse(
            EducationDocumentDownloadService.DownloadedEducationDocument document
    ) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(document.originalFilename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(document.contentType()))
                .contentLength(document.sizeBytes())
                .body(document.resource());
    }
}
