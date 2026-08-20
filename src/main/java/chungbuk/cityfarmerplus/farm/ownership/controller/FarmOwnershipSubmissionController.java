package chungbuk.cityfarmerplus.farm.ownership.controller;

import chungbuk.cityfarmerplus.auth.exception.AuthException;
import chungbuk.cityfarmerplus.farm.ownership.dto.FarmOwnershipSubmissionResponse;
import chungbuk.cityfarmerplus.farm.ownership.service.FarmOwnershipSubmissionService;
import chungbuk.cityfarmerplus.farm.ownership.service.FarmOwnershipQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/farm-profiles/me/ownership-submissions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('FARM')")
public class FarmOwnershipSubmissionController {

    private final FarmOwnershipSubmissionService submissionService;
    private final FarmOwnershipQueryService queryService;

    @GetMapping
    public ResponseEntity<List<FarmOwnershipSubmissionResponse>> getMine(
            Authentication authentication
    ) {
        return ResponseEntity.ok(queryService.getMine(getUserId(authentication)));
    }

    @GetMapping("/{submissionId}")
    public ResponseEntity<FarmOwnershipSubmissionResponse> getMine(
            Authentication authentication,
            @PathVariable Long submissionId
    ) {
        return ResponseEntity.ok(
                queryService.getMine(getUserId(authentication), submissionId)
        );
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FarmOwnershipSubmissionResponse> submit(
            Authentication authentication,
            @RequestPart(name = "documents", required = false)
            List<MultipartFile> documents
    ) {
        FarmOwnershipSubmissionResponse response = submissionService.submit(
                getUserId(authentication),
                documents
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private Long getUserId(Authentication authentication) {
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            throw AuthException.invalidAuthentication();
        }
    }
}
