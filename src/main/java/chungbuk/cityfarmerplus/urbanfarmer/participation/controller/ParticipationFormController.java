package chungbuk.cityfarmerplus.urbanfarmer.participation.controller;

import chungbuk.cityfarmerplus.common.web.AuthenticatedUser;
import chungbuk.cityfarmerplus.urbanfarmer.participation.dto.ParticipationFormRequest;
import chungbuk.cityfarmerplus.urbanfarmer.participation.dto.ParticipationFormResponse;
import chungbuk.cityfarmerplus.urbanfarmer.participation.service.ParticipationFormService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/urban-farmers/me/participation-forms")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('URBAN_FARMER')")
public class ParticipationFormController {

    private final ParticipationFormService formService;

    @GetMapping("/{programYear}")
    public ResponseEntity<ParticipationFormResponse> getMine(
            Authentication authentication,
            @PathVariable
            @Min(value = 2000, message = "사업연도는 2000 이상이어야 합니다.")
            @Max(value = 2100, message = "사업연도는 2100 이하여야 합니다.")
            int programYear
    ) {
        return ResponseEntity.ok(formService.getMine(
                AuthenticatedUser.id(authentication),
                programYear
        ));
    }

    @PutMapping("/{programYear}")
    public ResponseEntity<ParticipationFormResponse> save(
            Authentication authentication,
            @PathVariable
            @Min(value = 2000, message = "사업연도는 2000 이상이어야 합니다.")
            @Max(value = 2100, message = "사업연도는 2100 이하여야 합니다.")
            int programYear,
            @Valid @RequestBody ParticipationFormRequest request
    ) {
        return ResponseEntity.ok(formService.save(
                AuthenticatedUser.id(authentication),
                programYear,
                request
        ));
    }

    @PostMapping("/{programYear}/submit")
    public ResponseEntity<ParticipationFormResponse> submit(
            Authentication authentication,
            @PathVariable
            @Min(value = 2000, message = "사업연도는 2000 이상이어야 합니다.")
            @Max(value = 2100, message = "사업연도는 2100 이하여야 합니다.")
            int programYear,
            @Valid @RequestBody ParticipationFormRequest request
    ) {
        return ResponseEntity.ok(formService.submit(
                AuthenticatedUser.id(authentication),
                programYear,
                request
        ));
    }
}
