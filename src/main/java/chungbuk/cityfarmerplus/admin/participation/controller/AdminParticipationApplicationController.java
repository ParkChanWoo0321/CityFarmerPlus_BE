package chungbuk.cityfarmerplus.admin.participation.controller;

import chungbuk.cityfarmerplus.admin.participation.dto.ParticipationRejectRequest;
import chungbuk.cityfarmerplus.admin.participation.service.AdminParticipationApplicationService;
import chungbuk.cityfarmerplus.common.web.AuthenticatedUser;
import chungbuk.cityfarmerplus.urbanfarmer.participation.dto.ParticipationApplicationResponse;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/participation-applications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CENTER_ADMIN')")
public class AdminParticipationApplicationController {

    private final AdminParticipationApplicationService applicationService;

    @GetMapping
    public ResponseEntity<List<ParticipationApplicationResponse>> list() {
        return ResponseEntity.ok(applicationService.list());
    }

    @GetMapping("/{applicationId}")
    public ResponseEntity<ParticipationApplicationResponse> getDetail(
            @PathVariable Long applicationId
    ) {
        return ResponseEntity.ok(applicationService.getDetail(applicationId));
    }

    @PostMapping("/{applicationId}/approve")
    public ResponseEntity<ParticipationApplicationResponse> approve(
            Authentication authentication,
            @PathVariable Long applicationId
    ) {
        return ResponseEntity.ok(applicationService.approve(
                AuthenticatedUser.id(authentication),
                applicationId
        ));
    }

    @PostMapping("/{applicationId}/reject")
    public ResponseEntity<ParticipationApplicationResponse> reject(
            Authentication authentication,
            @PathVariable Long applicationId,
            @Valid @RequestBody ParticipationRejectRequest request
    ) {
        return ResponseEntity.ok(applicationService.reject(
                AuthenticatedUser.id(authentication),
                applicationId,
                request
        ));
    }
}
