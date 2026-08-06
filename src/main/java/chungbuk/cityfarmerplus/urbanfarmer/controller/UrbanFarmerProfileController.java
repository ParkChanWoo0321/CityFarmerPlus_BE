package chungbuk.cityfarmerplus.urbanfarmer.controller;

import chungbuk.cityfarmerplus.urbanfarmer.dto.UrbanFarmerProfileRequest;
import chungbuk.cityfarmerplus.urbanfarmer.dto.UrbanFarmerProfileResponse;
import chungbuk.cityfarmerplus.urbanfarmer.entity.UrbanFarmerProfile;
import chungbuk.cityfarmerplus.urbanfarmer.service.UrbanFarmerProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/urban-farmer/profile")
@RequiredArgsConstructor
public class UrbanFarmerProfileController {

    private final UrbanFarmerProfileService urbanFarmerProfileService;

    @PostMapping
    public ResponseEntity<UrbanFarmerProfileResponse> register(
            @Valid @RequestBody UrbanFarmerProfileRequest request,
            Authentication authentication
    ) {
        Long userId = (Long) authentication.getPrincipal();
        UrbanFarmerProfile profile = urbanFarmerProfileService.register(userId, request);
        return ResponseEntity.ok(new UrbanFarmerProfileResponse(profile));
    }

    @GetMapping
    public ResponseEntity<UrbanFarmerProfileResponse> getMyProfile(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        UrbanFarmerProfile profile = urbanFarmerProfileService.getMyProfile(userId);
        return ResponseEntity.ok(new UrbanFarmerProfileResponse(profile));
    }

    @PutMapping
    public ResponseEntity<UrbanFarmerProfileResponse> updateMyProfile(
            @Valid @RequestBody UrbanFarmerProfileRequest request,
            Authentication authentication
    ) {
        Long userId = (Long) authentication.getPrincipal();
        UrbanFarmerProfile profile = urbanFarmerProfileService.updateMyProfile(userId, request);
        return ResponseEntity.ok(new UrbanFarmerProfileResponse(profile));
    }
}
