package chungbuk.cityfarmerplus.jobposting.controller;

import chungbuk.cityfarmerplus.common.web.AuthenticatedUser;
import chungbuk.cityfarmerplus.common.web.PageResponse;
import chungbuk.cityfarmerplus.jobposting.dto.FarmJobPostingDisplayStatus;
import chungbuk.cityfarmerplus.jobposting.dto.ApplicantPreferenceRequest;
import chungbuk.cityfarmerplus.jobposting.dto.JobPostingResponse;
import chungbuk.cityfarmerplus.jobposting.dto.JobPostingUpsertRequest;
import chungbuk.cityfarmerplus.jobposting.dto.JobPostingReviewResponse;
import chungbuk.cityfarmerplus.jobposting.service.FarmJobPostingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/farm/job-postings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('FARM')")
@Validated
public class FarmJobPostingController {

    private final FarmJobPostingService service;

    @PostMapping
    public ResponseEntity<JobPostingResponse> create(
            Authentication authentication,
            @RequestParam(defaultValue = "false") boolean submitForReview,
            @Valid @RequestBody JobPostingUpsertRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(
                        AuthenticatedUser.id(authentication),
                        request,
                        submitForReview
                ));
    }

    @GetMapping
    public PageResponse<JobPostingResponse> getMine(
            Authentication authentication,
            @RequestParam(required = false) FarmJobPostingDisplayStatus displayStatus,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return service.getMine(
                AuthenticatedUser.id(authentication),
                displayStatus,
                page,
                size
        );
    }

    @GetMapping("/{postingId}")
    public JobPostingResponse getMine(
            Authentication authentication,
            @PathVariable Long postingId
    ) {
        return service.getMine(AuthenticatedUser.id(authentication), postingId);
    }

    @GetMapping("/{postingId}/review-history")
    public List<JobPostingReviewResponse> reviewHistory(
            Authentication authentication,
            @PathVariable Long postingId
    ) {
        return service.getReviewHistory(
                AuthenticatedUser.id(authentication),
                postingId
        );
    }

    @PatchMapping("/{postingId}")
    public JobPostingResponse update(
            Authentication authentication,
            @PathVariable Long postingId,
            @Valid @RequestBody JobPostingUpsertRequest request
    ) {
        return service.update(AuthenticatedUser.id(authentication), postingId, request);
    }

    @DeleteMapping("/{postingId}")
    public ResponseEntity<Void> deleteDraft(
            Authentication authentication,
            @PathVariable Long postingId
    ) {
        service.deleteDraft(AuthenticatedUser.id(authentication), postingId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{postingId}/submit-review")
    public JobPostingResponse submitReview(
            Authentication authentication,
            @PathVariable Long postingId
    ) {
        return service.submitReview(AuthenticatedUser.id(authentication), postingId);
    }

    @PostMapping("/{postingId}/withdraw-review")
    public JobPostingResponse withdrawReview(
            Authentication authentication,
            @PathVariable Long postingId
    ) {
        return service.withdrawReview(AuthenticatedUser.id(authentication), postingId);
    }

    @PatchMapping("/{postingId}/applicant-preference")
    public JobPostingResponse updateApplicantPreference(
            Authentication authentication,
            @PathVariable Long postingId,
            @Valid @RequestBody ApplicantPreferenceRequest request
    ) {
        return service.updateApplicantPreference(
                AuthenticatedUser.id(authentication),
                postingId,
                request.applicantPreference()
        );
    }

    @PostMapping("/{postingId}/cancel")
    public JobPostingResponse cancel(
            Authentication authentication,
            @PathVariable Long postingId
    ) {
        return service.cancel(AuthenticatedUser.id(authentication), postingId);
    }
}
