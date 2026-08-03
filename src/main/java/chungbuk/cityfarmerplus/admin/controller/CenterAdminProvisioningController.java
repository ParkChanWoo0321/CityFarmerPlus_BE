package chungbuk.cityfarmerplus.admin.controller;

import chungbuk.cityfarmerplus.admin.dto.CenterAdminCreateRequest;
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

    public static final String PROVISIONING_KEY_HEADER = "X-Admin-Provisioning-Key";

    private final CenterAdminProvisioningService provisioningService;

    @PostMapping
    public ResponseEntity<UserResponse> provision(
            @RequestHeader(name = PROVISIONING_KEY_HEADER, required = false)
            String provisioningKey,
            @Valid @RequestBody CenterAdminCreateRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(provisioningService.provision(provisioningKey, request));
    }
}
