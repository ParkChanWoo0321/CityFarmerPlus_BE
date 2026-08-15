package chungbuk.cityfarmerplus.urbanfarmer.participation.controller;

import chungbuk.cityfarmerplus.common.web.AuthenticatedUser;
import chungbuk.cityfarmerplus.urbanfarmer.participation.dto.ParticipationApplicationRequest;
import chungbuk.cityfarmerplus.urbanfarmer.participation.dto.ParticipationApplicationResponse;
import chungbuk.cityfarmerplus.urbanfarmer.participation.dto.ParticipationApplicationUpdateRequest;
import chungbuk.cityfarmerplus.urbanfarmer.participation.service.ParticipationApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/urban-farmers/me/participation-applications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('URBAN_FARMER')")
public class ParticipationApplicationController {

    private final ParticipationApplicationService applicationService;

    @PostMapping
    public ResponseEntity<ParticipationApplicationResponse> create(
            Authentication authentication,
            @Valid @RequestBody ParticipationApplicationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                applicationService.create(AuthenticatedUser.id(authentication), request)
        );
    }

    @GetMapping
    public ResponseEntity<List<ParticipationApplicationResponse>> getMine(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                applicationService.getMine(AuthenticatedUser.id(authentication))
        );
    }

    @GetMapping("/{applicationId}")
    public ResponseEntity<ParticipationApplicationResponse> getMine(
            Authentication authentication,
            @PathVariable Long applicationId
    ) {
        return ResponseEntity.ok(applicationService.getMine(
                AuthenticatedUser.id(authentication),
                applicationId
        ));
    }

    @PatchMapping("/{applicationId}")
    public ResponseEntity<ParticipationApplicationResponse> update(
            Authentication authentication,
            @PathVariable Long applicationId,
            @Valid @RequestBody ParticipationApplicationUpdateRequest request
    ) {
        return ResponseEntity.ok(applicationService.update(
                AuthenticatedUser.id(authentication),
                applicationId,
                request
        ));
    }

    @DeleteMapping("/{applicationId}")
    public ResponseEntity<Void> deleteDraft(
            Authentication authentication,
            @PathVariable Long applicationId
    ) {
        applicationService.deleteDraft(
                AuthenticatedUser.id(authentication),
                applicationId
        );
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{applicationId}/submit")
    public ResponseEntity<ParticipationApplicationResponse> submit(
            Authentication authentication,
            @PathVariable Long applicationId
    ) {
        return ResponseEntity.ok(applicationService.submit(
                AuthenticatedUser.id(authentication),
                applicationId
        ));
    }

    @PostMapping("/{applicationId}/cancel")
    public ResponseEntity<ParticipationApplicationResponse> cancel(
            Authentication authentication,
            @PathVariable Long applicationId
    ) {
        return ResponseEntity.ok(applicationService.cancel(
                AuthenticatedUser.id(authentication),
                applicationId
        ));
    }
}
