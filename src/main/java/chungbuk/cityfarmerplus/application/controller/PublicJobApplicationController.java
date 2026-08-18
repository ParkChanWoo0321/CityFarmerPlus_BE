package chungbuk.cityfarmerplus.application.controller;

import chungbuk.cityfarmerplus.application.dto.JobApplicationResponse;
import chungbuk.cityfarmerplus.application.service.JobApplicationService;
import chungbuk.cityfarmerplus.common.web.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/job-postings/{postingId}/applications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('URBAN_FARMER')")
public class PublicJobApplicationController {

    private final JobApplicationService service;

    @PostMapping
    public ResponseEntity<JobApplicationResponse> apply(
            Authentication authentication,
            @PathVariable Long postingId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.apply(AuthenticatedUser.id(authentication), postingId));
    }
}
