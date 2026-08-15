package chungbuk.cityfarmerplus.urbanfarmer.preference.controller;

import chungbuk.cityfarmerplus.common.web.AuthenticatedUser;
import chungbuk.cityfarmerplus.urbanfarmer.preference.dto.WorkPreferenceRequest;
import chungbuk.cityfarmerplus.urbanfarmer.preference.dto.WorkPreferenceResponse;
import chungbuk.cityfarmerplus.urbanfarmer.preference.service.WorkPreferenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/urban-farmers/me/work-preference")
@RequiredArgsConstructor
@PreAuthorize("hasRole('URBAN_FARMER')")
public class WorkPreferenceController {

    private final WorkPreferenceService preferenceService;

    @GetMapping
    public ResponseEntity<WorkPreferenceResponse> getMine(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                preferenceService.getMine(AuthenticatedUser.id(authentication))
        );
    }

    @PutMapping
    public ResponseEntity<WorkPreferenceResponse> upsert(
            Authentication authentication,
            @Valid @RequestBody WorkPreferenceRequest request
    ) {
        return ResponseEntity.ok(preferenceService.upsert(
                AuthenticatedUser.id(authentication),
                request
        ));
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteMine(Authentication authentication) {
        preferenceService.deleteMine(AuthenticatedUser.id(authentication));
        return ResponseEntity.noContent().build();
    }
}
