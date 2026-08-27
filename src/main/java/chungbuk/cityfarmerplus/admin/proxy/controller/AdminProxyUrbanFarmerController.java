package chungbuk.cityfarmerplus.admin.proxy.controller;

import chungbuk.cityfarmerplus.admin.proxy.dto.ProxyAccountRequest;
import chungbuk.cityfarmerplus.admin.proxy.dto.ProxyEducationSubmissionRequest;
import chungbuk.cityfarmerplus.admin.proxy.dto.ProxyParticipationApplicationRequest;
import chungbuk.cityfarmerplus.admin.proxy.dto.ProxyParticipationSubmitRequest;
import chungbuk.cityfarmerplus.admin.proxy.dto.ProxyUrbanFarmerProfileRequest;
import chungbuk.cityfarmerplus.admin.proxy.dto.ProxyWorkPreferenceRequest;
import chungbuk.cityfarmerplus.admin.proxy.service.AdminProxyUrbanFarmerService;
import chungbuk.cityfarmerplus.auth.dto.UserResponse;
import chungbuk.cityfarmerplus.common.web.AuthenticatedUser;
import chungbuk.cityfarmerplus.education.dto.EducationSubmissionResponse;
import chungbuk.cityfarmerplus.urbanfarmer.participation.dto.ParticipationApplicationResponse;
import chungbuk.cityfarmerplus.urbanfarmer.preference.dto.WorkPreferenceResponse;
import chungbuk.cityfarmerplus.urbanfarmer.profile.dto.UrbanFarmerProfileResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin/proxy/urban-farmers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CENTER_ADMIN')")
public class AdminProxyUrbanFarmerController {

    private final AdminProxyUrbanFarmerService proxyService;

    @PostMapping
    public ResponseEntity<UserResponse> createAccount(
            Authentication authentication,
            @Valid @RequestBody ProxyAccountRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(proxyService.createAccount(AuthenticatedUser.id(authentication), request));
    }

    @PostMapping("/{userId}/profile")
    public ResponseEntity<UrbanFarmerProfileResponse> registerProfile(
            Authentication authentication,
            @PathVariable Long userId,
            @Valid @RequestBody ProxyUrbanFarmerProfileRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(proxyService.registerProfile(
                        AuthenticatedUser.id(authentication),
                        userId,
                        request
                ));
    }

    @PutMapping("/{userId}/work-preference")
    public ResponseEntity<WorkPreferenceResponse> registerWorkPreference(
            Authentication authentication,
            @PathVariable Long userId,
            @Valid @RequestBody ProxyWorkPreferenceRequest request
    ) {
        return ResponseEntity.ok(proxyService.registerWorkPreference(
                AuthenticatedUser.id(authentication),
                userId,
                request
        ));
    }

    @PostMapping("/{userId}/participation-applications")
    public ResponseEntity<ParticipationApplicationResponse> createParticipationApplication(
            Authentication authentication,
            @PathVariable Long userId,
            @Valid @RequestBody ProxyParticipationApplicationRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(proxyService.createParticipationApplication(
                        AuthenticatedUser.id(authentication),
                        userId,
                        request
                ));
    }

    @PostMapping("/{userId}/participation-applications/{applicationId}/submit")
    public ResponseEntity<ParticipationApplicationResponse> submitParticipationApplication(
            Authentication authentication,
            @PathVariable Long userId,
            @PathVariable Long applicationId,
            @Valid @RequestBody ProxyParticipationSubmitRequest request
    ) {
        return ResponseEntity.ok(proxyService.submitParticipationApplication(
                AuthenticatedUser.id(authentication),
                userId,
                applicationId,
                request
        ));
    }

    @PostMapping(value = "/{userId}/education-submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EducationSubmissionResponse> submitEducationCertification(
            Authentication authentication,
            @PathVariable Long userId,
            @Valid @RequestPart("request") ProxyEducationSubmissionRequest request,
            @RequestPart(name = "documents", required = false) List<MultipartFile> documents
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(proxyService.submitEducationCertification(
                        AuthenticatedUser.id(authentication),
                        userId,
                        request,
                        documents
                ));
    }
}
