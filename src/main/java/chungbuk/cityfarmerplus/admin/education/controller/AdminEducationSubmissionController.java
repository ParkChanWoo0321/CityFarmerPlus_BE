package chungbuk.cityfarmerplus.admin.education.controller;

import chungbuk.cityfarmerplus.admin.education.dto.EducationApproveRequest;
import chungbuk.cityfarmerplus.admin.education.dto.EducationRejectRequest;
import chungbuk.cityfarmerplus.admin.education.service.AdminEducationSubmissionService;
import chungbuk.cityfarmerplus.common.web.AuthenticatedUser;
import chungbuk.cityfarmerplus.education.dto.EducationSubmissionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/education/submissions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CENTER_ADMIN')")
public class AdminEducationSubmissionController {

    private final AdminEducationSubmissionService submissionService;

    @GetMapping
    public ResponseEntity<Page<EducationSubmissionResponse>> list(Pageable pageable) {
        return ResponseEntity.ok(submissionService.list(pageable));
    }

    @GetMapping("/{submissionId}")
    public ResponseEntity<EducationSubmissionResponse> getDetail(
            @PathVariable Long submissionId
    ) {
        return ResponseEntity.ok(submissionService.getDetail(submissionId));
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
}
