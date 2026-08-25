package chungbuk.cityfarmerplus.admin.jobposting.controller;

import chungbuk.cityfarmerplus.admin.jobposting.dto.JobPostingRejectRequest;
import chungbuk.cityfarmerplus.admin.jobposting.service.AdminJobPostingService;
import chungbuk.cityfarmerplus.common.web.AuthenticatedUser;
import chungbuk.cityfarmerplus.jobposting.dto.JobPostingResponse;
import chungbuk.cityfarmerplus.jobposting.dto.JobPostingReviewResponse;
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
@RequestMapping("/api/admin/job-postings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CENTER_ADMIN')")
public class AdminJobPostingController {

    private final AdminJobPostingService jobPostingService;

    @GetMapping
    public ResponseEntity<Page<JobPostingResponse>> list(Pageable pageable) {
        return ResponseEntity.ok(jobPostingService.list(pageable));
    }

    @PostMapping("/{postingId}/approve")
    public ResponseEntity<JobPostingReviewResponse> approve(
            Authentication authentication,
            @PathVariable Long postingId
    ) {
        return ResponseEntity.ok(jobPostingService.approve(
                AuthenticatedUser.id(authentication),
                postingId
        ));
    }

    @PostMapping("/{postingId}/reject")
    public ResponseEntity<JobPostingReviewResponse> reject(
            Authentication authentication,
            @PathVariable Long postingId,
            @Valid @RequestBody JobPostingRejectRequest request
    ) {
        return ResponseEntity.ok(jobPostingService.reject(
                AuthenticatedUser.id(authentication),
                postingId,
                request
        ));
    }
}
