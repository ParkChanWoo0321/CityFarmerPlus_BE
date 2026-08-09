package chungbuk.cityfarmerplus.urbanfarmer.controller;

import chungbuk.cityfarmerplus.urbanfarmer.dto.EducationStatusResponse;
import chungbuk.cityfarmerplus.urbanfarmer.entity.UrbanFarmerProfile;
import chungbuk.cityfarmerplus.urbanfarmer.service.UrbanFarmerEducationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/urban-farmer/education")
@RequiredArgsConstructor
public class UrbanFarmerEducationController {

    private final UrbanFarmerEducationService urbanFarmerEducationService;

    @GetMapping
    public ResponseEntity<EducationStatusResponse> getStatus(Authentication authentication) {
        Long userId = Long.valueOf(authentication.getName());
        UrbanFarmerProfile profile = urbanFarmerEducationService.getStatus(userId);
        return ResponseEntity.ok(new EducationStatusResponse(profile));
    }

    @PostMapping("/certificate")
    public ResponseEntity<EducationStatusResponse> registerCertificate(Authentication authentication) {
        Long userId = Long.valueOf(authentication.getName());
        UrbanFarmerProfile profile = urbanFarmerEducationService.registerCertificate(userId);
        return ResponseEntity.ok(new EducationStatusResponse(profile));
    }
}
