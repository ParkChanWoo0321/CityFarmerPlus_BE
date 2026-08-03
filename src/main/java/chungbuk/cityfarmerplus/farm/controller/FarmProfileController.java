package chungbuk.cityfarmerplus.farm.controller;

import chungbuk.cityfarmerplus.auth.exception.AuthException;
import chungbuk.cityfarmerplus.farm.dto.FarmProfileCreateRequest;
import chungbuk.cityfarmerplus.farm.dto.FarmProfileResponse;
import chungbuk.cityfarmerplus.farm.service.FarmProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/farm-profiles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('FARM')")
public class FarmProfileController {

    private final FarmProfileService farmProfileService;

    @PostMapping
    public ResponseEntity<FarmProfileResponse> create(
            Authentication authentication,
            @Valid @RequestBody FarmProfileCreateRequest request
    ) {
        return ResponseEntity
                .created(URI.create("/api/farm-profiles/me"))
                .body(farmProfileService.create(getUserId(authentication), request));
    }

    @GetMapping("/me")
    public ResponseEntity<FarmProfileResponse> getMine(Authentication authentication) {
        return ResponseEntity.ok(
                farmProfileService.getMine(getUserId(authentication))
        );
    }

    private Long getUserId(Authentication authentication) {
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            throw AuthException.invalidAuthentication();
        }
    }
}
