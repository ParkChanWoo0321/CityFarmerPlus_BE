package chungbuk.cityfarmerplus.ai.support;

import chungbuk.cityfarmerplus.common.web.AuthenticatedUser;
import chungbuk.cityfarmerplus.common.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/support/messages")
@RequiredArgsConstructor
@Validated
public class AiSupportController {

    private final AiSupportService service;

    @PostMapping
    public SupportMessageResponse send(
            Authentication authentication,
            @Valid @RequestBody SupportMessageRequest request
    ) {
        return service.send(AuthenticatedUser.id(authentication), request);
    }

    @GetMapping
    public PageResponse<SupportMessageResponse> getMine(
            Authentication authentication,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return service.getMine(AuthenticatedUser.id(authentication), page, size);
    }
}
