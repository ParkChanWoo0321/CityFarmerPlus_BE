package chungbuk.cityfarmerplus.urbanfarmer.profile.controller;

import chungbuk.cityfarmerplus.common.web.AuthenticatedUser;
import chungbuk.cityfarmerplus.urbanfarmer.profile.dto.UrbanFarmerProfileRequest;
import chungbuk.cityfarmerplus.urbanfarmer.profile.dto.UrbanFarmerProfileResponse;
import chungbuk.cityfarmerplus.urbanfarmer.profile.service.UrbanFarmerProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/urban-farmers/me/profile")
@RequiredArgsConstructor
@PreAuthorize("hasRole('URBAN_FARMER')")
public class UrbanFarmerProfileController {

    private final UrbanFarmerProfileService profileService;

    @PostMapping
    public ResponseEntity<UrbanFarmerProfileResponse> create(
            Authentication authentication,
            @Valid @RequestBody UrbanFarmerProfileRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(profileService.create(AuthenticatedUser.id(authentication), request));
    }

    @GetMapping
    public ResponseEntity<UrbanFarmerProfileResponse> getMine(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                profileService.getMine(AuthenticatedUser.id(authentication))
        );
    }

    @PatchMapping
    public ResponseEntity<UrbanFarmerProfileResponse> update(
            Authentication authentication,
            @Valid @RequestBody UrbanFarmerProfileRequest request
    ) {
        return ResponseEntity.ok(
                profileService.update(AuthenticatedUser.id(authentication), request)
        );
    }
}
