package chungbuk.cityfarmerplus.admin.controller;

import chungbuk.cityfarmerplus.admin.dto.AdminUrbanFarmerDetailResponse;
import chungbuk.cityfarmerplus.admin.dto.AdminUrbanFarmerListItemResponse;
import chungbuk.cityfarmerplus.admin.service.AdminUrbanFarmerService;
import chungbuk.cityfarmerplus.urbanfarmer.dto.UrbanFarmerProfileRequest;
import chungbuk.cityfarmerplus.urbanfarmer.dto.UrbanFarmerProfileResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/urban-farmers")
@RequiredArgsConstructor
public class AdminUrbanFarmerController {

    private final AdminUrbanFarmerService adminUrbanFarmerService;

    @GetMapping
    public ResponseEntity<Page<AdminUrbanFarmerListItemResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(adminUrbanFarmerService.list(PageRequest.of(page, size)));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<AdminUrbanFarmerDetailResponse> getDetail(@PathVariable Long userId) {
        return ResponseEntity.ok(adminUrbanFarmerService.getDetail(userId));
    }

    @PostMapping("/{userId}/verify-eligibility")
    public ResponseEntity<AdminUrbanFarmerDetailResponse> verifyEligibility(@PathVariable Long userId) {
        return ResponseEntity.ok(adminUrbanFarmerService.verifyEligibility(userId));
    }

    @PostMapping("/{userId}/education/approve")
    public ResponseEntity<AdminUrbanFarmerDetailResponse> approveEducation(@PathVariable Long userId) {
        return ResponseEntity.ok(adminUrbanFarmerService.approveEducation(userId));
    }

    @PostMapping("/{userId}/profile")
    public ResponseEntity<UrbanFarmerProfileResponse> registerProfile(
            @PathVariable Long userId,
            @Valid @RequestBody UrbanFarmerProfileRequest request
    ) {
        return ResponseEntity.ok(adminUrbanFarmerService.registerProfile(userId, request));
    }
}
