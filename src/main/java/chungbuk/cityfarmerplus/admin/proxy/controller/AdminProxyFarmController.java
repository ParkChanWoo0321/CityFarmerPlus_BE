package chungbuk.cityfarmerplus.admin.proxy.controller;

import chungbuk.cityfarmerplus.admin.proxy.dto.ProxyAccountRequest;
import chungbuk.cityfarmerplus.admin.proxy.dto.ProxyFarmOwnershipSubmissionRequest;
import chungbuk.cityfarmerplus.admin.proxy.dto.ProxyFarmProfileRequest;
import chungbuk.cityfarmerplus.admin.proxy.dto.ProxyJobPostingDraftRequest;
import chungbuk.cityfarmerplus.admin.proxy.service.AdminProxyFarmService;
import chungbuk.cityfarmerplus.auth.dto.UserResponse;
import chungbuk.cityfarmerplus.common.web.AuthenticatedUser;
import chungbuk.cityfarmerplus.farm.dto.FarmProfileResponse;
import chungbuk.cityfarmerplus.farm.ownership.dto.FarmOwnershipSubmissionResponse;
import chungbuk.cityfarmerplus.jobposting.dto.JobPostingResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin/proxy/farms")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CENTER_ADMIN')")
public class AdminProxyFarmController {

    private final AdminProxyFarmService proxyService;

    @PostMapping
    public ResponseEntity<UserResponse> createAccount(
            Authentication authentication,
            @Valid @RequestBody ProxyAccountRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(proxyService.createAccount(AuthenticatedUser.id(authentication), request));
    }

    @PostMapping("/{userId}/profile")
    public ResponseEntity<FarmProfileResponse> registerProfile(
            Authentication authentication,
            @PathVariable Long userId,
            @Valid @RequestBody ProxyFarmProfileRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(proxyService.registerProfile(
                        AuthenticatedUser.id(authentication),
                        userId,
                        request
                ));
    }

    @PostMapping(value = "/{userId}/ownership-submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FarmOwnershipSubmissionResponse> submitOwnershipDocuments(
            Authentication authentication,
            @PathVariable Long userId,
            @Valid @RequestPart("request") ProxyFarmOwnershipSubmissionRequest request,
            @RequestPart(name = "documents", required = false) List<MultipartFile> documents
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(proxyService.submitOwnershipDocuments(
                        AuthenticatedUser.id(authentication),
                        userId,
                        request,
                        documents
                ));
    }

    @PostMapping("/{userId}/job-postings")
    public ResponseEntity<JobPostingResponse> createJobPostingDraft(
            Authentication authentication,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "false") boolean submitForReview,
            @Valid @RequestBody ProxyJobPostingDraftRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(proxyService.createJobPostingDraft(
                        AuthenticatedUser.id(authentication),
                        userId,
                        request,
                        submitForReview
                ));
    }
}
