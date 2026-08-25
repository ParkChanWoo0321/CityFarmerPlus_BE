package chungbuk.cityfarmerplus.ai.jobposting;

import chungbuk.cityfarmerplus.common.web.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/job-posting-previews")
@RequiredArgsConstructor
@PreAuthorize("hasRole('FARM')")
public class AiJobPostingController {

    private final AiJobPostingService service;

    @PostMapping
    public AiJobPostingPreviewResponse preview(
            Authentication authentication,
            @Valid @RequestBody AiJobPostingPreviewRequest request
    ) {
        return service.preview(AuthenticatedUser.id(authentication), request);
    }
}
