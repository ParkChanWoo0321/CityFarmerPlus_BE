package chungbuk.cityfarmerplus.work.controller;

import chungbuk.cityfarmerplus.common.web.AuthenticatedUser;
import chungbuk.cityfarmerplus.common.web.PageResponse;
import chungbuk.cityfarmerplus.work.dto.WorkAssignmentResponse;
import chungbuk.cityfarmerplus.work.dto.WorkAssignmentView;
import chungbuk.cityfarmerplus.work.guide.WorkGuideResponse;
import chungbuk.cityfarmerplus.work.guide.WorkGuideService;
import chungbuk.cityfarmerplus.work.service.WorkAssignmentService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/urban-farmers/me/work-assignments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('URBAN_FARMER')")
@Validated
public class UrbanFarmerWorkAssignmentController {

    private final WorkAssignmentService service;
    private final WorkGuideService guideService;

    @GetMapping
    public PageResponse<WorkAssignmentResponse> getMine(
            Authentication authentication,
            @RequestParam(defaultValue = "ALL") WorkAssignmentView view,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return service.getUrbanFarmerAssignments(
                AuthenticatedUser.id(authentication), view, page, size);
    }

    @GetMapping("/{assignmentId}")
    public WorkAssignmentResponse getMine(
            Authentication authentication,
            @PathVariable Long assignmentId
    ) {
        return service.getUrbanFarmerAssignment(
                AuthenticatedUser.id(authentication), assignmentId);
    }

    @GetMapping("/{assignmentId}/guide")
    public WorkGuideResponse getGuide(
            Authentication authentication,
            @PathVariable Long assignmentId
    ) {
        return guideService.get(AuthenticatedUser.id(authentication), assignmentId);
    }
}
