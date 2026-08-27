package chungbuk.cityfarmerplus.admin.controller;

import chungbuk.cityfarmerplus.admin.dto.CenterAdminProvisioningRequest;
import chungbuk.cityfarmerplus.admin.service.CenterAdminProvisioningService;
import chungbuk.cityfarmerplus.auth.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/center-admins")
@RequiredArgsConstructor
public class CenterAdminProvisioningController {

    private final CenterAdminProvisioningService centerAdminProvisioningService;

    @PostMapping
    public ResponseEntity<UserResponse> provision(
            @RequestHeader("X-Admin-Provisioning-Key") String provisioningKey,
            @Valid @RequestBody CenterAdminProvisioningRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(centerAdminProvisioningService.provision(provisioningKey, request));
    }
}
