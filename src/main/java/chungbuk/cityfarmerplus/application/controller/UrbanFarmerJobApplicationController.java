package chungbuk.cityfarmerplus.application.controller;

import chungbuk.cityfarmerplus.application.dto.JobApplicationResponse;
import chungbuk.cityfarmerplus.application.service.JobApplicationService;
import chungbuk.cityfarmerplus.common.web.AuthenticatedUser;
import chungbuk.cityfarmerplus.common.web.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/urban-farmers/me/job-applications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('URBAN_FARMER')")
@Validated
public class UrbanFarmerJobApplicationController {

    private final JobApplicationService service;

    @GetMapping
    public PageResponse<JobApplicationResponse> getMine(
            Authentication authentication,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return service.getMine(AuthenticatedUser.id(authentication), page, size);
    }

    @GetMapping("/{applicationId}")
    public JobApplicationResponse getMine(
            Authentication authentication,
            @PathVariable Long applicationId
    ) {
        return service.getMine(AuthenticatedUser.id(authentication), applicationId);
    }

    @PostMapping("/{applicationId}/withdraw")
    public JobApplicationResponse withdraw(
            Authentication authentication,
            @PathVariable Long applicationId
    ) {
        return service.withdraw(AuthenticatedUser.id(authentication), applicationId);
    }
}
