package chungbuk.cityfarmerplus.admin.education.controller;

import chungbuk.cityfarmerplus.admin.education.dto.EducationApproveRequest;
import chungbuk.cityfarmerplus.admin.education.dto.EducationRejectRequest;
import chungbuk.cityfarmerplus.admin.education.service.AdminEducationSubmissionService;
import chungbuk.cityfarmerplus.common.web.AuthenticatedUser;
import chungbuk.cityfarmerplus.common.web.PageResponse;
import chungbuk.cityfarmerplus.education.dto.EducationSubmissionResponse;
import chungbuk.cityfarmerplus.education.service.EducationDocumentDownloadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/admin/education/submissions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CENTER_ADMIN')")
public class AdminEducationSubmissionController {

    private final AdminEducationSubmissionService submissionService;
    private final EducationDocumentDownloadService documentDownloadService;

    @GetMapping
    public ResponseEntity<PageResponse<EducationSubmissionResponse>> list(Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(
                submissionService.list(pageable),
                response -> response
        ));
    }

    @GetMapping("/{submissionId}")
    public ResponseEntity<EducationSubmissionResponse> getDetail(
            @PathVariable Long submissionId
    ) {
        return ResponseEntity.ok(submissionService.getDetail(submissionId));
    }

    @GetMapping("/{submissionId}/documents/{documentId}")
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable Long submissionId,
            @PathVariable Long documentId
    ) {
        EducationDocumentDownloadService.DownloadedEducationDocument document =
                documentDownloadService.downloadForAdmin(submissionId, documentId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(document.originalFilename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(resolveContentType(document.contentType()))
                .contentLength(document.sizeBytes())
                .body(document.resource());
    }

    @PostMapping("/{submissionId}/approve")
    public ResponseEntity<EducationSubmissionResponse> approve(
            Authentication authentication,
            @PathVariable Long submissionId,
            @Valid @RequestBody EducationApproveRequest request
    ) {
        return ResponseEntity.ok(submissionService.approve(
                AuthenticatedUser.id(authentication),
                submissionId,
                request
        ));
    }

    @PostMapping("/{submissionId}/reject")
    public ResponseEntity<EducationSubmissionResponse> reject(
            Authentication authentication,
            @PathVariable Long submissionId,
            @Valid @RequestBody EducationRejectRequest request
    ) {
        return ResponseEntity.ok(submissionService.reject(
                AuthenticatedUser.id(authentication),
                submissionId,
                request
        ));
    }

    private MediaType resolveContentType(String contentType) {
        try {
            return MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
