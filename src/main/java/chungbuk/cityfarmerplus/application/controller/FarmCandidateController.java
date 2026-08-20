package chungbuk.cityfarmerplus.application.controller;

import chungbuk.cityfarmerplus.application.dto.FarmOpinionRequest;
import chungbuk.cityfarmerplus.application.dto.JobCandidateResponse;
import chungbuk.cityfarmerplus.application.service.FarmCandidateService;
import chungbuk.cityfarmerplus.common.web.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/farm/job-postings/{postingId}/applications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('FARM')")
public class FarmCandidateController {

    private final FarmCandidateService service;

    @GetMapping
    public List<JobCandidateResponse> getCandidates(
            Authentication authentication,
            @PathVariable Long postingId
    ) {
        return service.getCandidates(AuthenticatedUser.id(authentication), postingId);
    }

    @PatchMapping("/{applicationId}/opinion")
    public JobCandidateResponse updateOpinion(
            Authentication authentication,
            @PathVariable Long postingId,
            @PathVariable Long applicationId,
            @Valid @RequestBody FarmOpinionRequest request
    ) {
        return service.updateOpinion(
                AuthenticatedUser.id(authentication),
                postingId,
                applicationId,
                request
        );
    }
}
