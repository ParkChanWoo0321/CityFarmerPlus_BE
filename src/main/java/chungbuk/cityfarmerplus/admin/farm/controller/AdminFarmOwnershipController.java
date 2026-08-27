package chungbuk.cityfarmerplus.admin.farm.controller;

import chungbuk.cityfarmerplus.admin.farm.dto.FarmOwnershipRejectRequest;
import chungbuk.cityfarmerplus.admin.farm.service.AdminFarmOwnershipService;
import chungbuk.cityfarmerplus.common.web.AuthenticatedUser;
import chungbuk.cityfarmerplus.farm.dto.FarmProfileResponse;
import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import chungbuk.cityfarmerplus.farm.ownership.dto.FarmOwnershipSubmissionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/farm-profiles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CENTER_ADMIN')")
public class AdminFarmOwnershipController {

    private final AdminFarmOwnershipService farmOwnershipService;

    @GetMapping
    public ResponseEntity<List<FarmProfileResponse>> list(
            @RequestParam FarmProfile.FarmProfileStatus status
    ) {
        return ResponseEntity.ok(farmOwnershipService.list(status));
    }

    @GetMapping("/{profileId}")
    public ResponseEntity<FarmOwnershipSubmissionResponse> getDetail(
            @PathVariable Long profileId
    ) {
        return ResponseEntity.ok(farmOwnershipService.getDetail(profileId));
    }

    @PostMapping("/{profileId}/ownership/approve")
    public ResponseEntity<FarmOwnershipSubmissionResponse> approve(
            Authentication authentication,
            @PathVariable Long profileId
    ) {
        return ResponseEntity.ok(farmOwnershipService.approve(
                AuthenticatedUser.id(authentication),
                profileId
        ));
    }

    @PostMapping("/{profileId}/ownership/reject")
    public ResponseEntity<FarmOwnershipSubmissionResponse> reject(
            Authentication authentication,
            @PathVariable Long profileId,
            @Valid @RequestBody FarmOwnershipRejectRequest request
    ) {
        return ResponseEntity.ok(farmOwnershipService.reject(
                AuthenticatedUser.id(authentication),
                profileId,
                request
        ));
    }
}
