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
        Long userId = (Long) authentication.getPrincipal();
        UrbanFarmerProfile profile = urbanFarmerEducationService.getStatus(userId);
        return ResponseEntity.ok(new EducationStatusResponse(profile));
    }

    @PostMapping("/certificate")
    public ResponseEntity<EducationStatusResponse> registerCertificate(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        UrbanFarmerProfile profile = urbanFarmerEducationService.registerCertificate(userId);
        return ResponseEntity.ok(new EducationStatusResponse(profile));
    }

    // 지금은 승인 절차 없이 바로 완료 처리. 추후 중개센터 관리자 기능 추가 시 승인 절차를 거치도록 변경 예정.
    @PostMapping("/complete")
    public ResponseEntity<EducationStatusResponse> completeEducation(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        UrbanFarmerProfile profile = urbanFarmerEducationService.completeEducation(userId);
        return ResponseEntity.ok(new EducationStatusResponse(profile));
    }
}
