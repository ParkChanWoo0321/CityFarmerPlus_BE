package chungbuk.cityfarmerplus.work.controller;

import chungbuk.cityfarmerplus.common.web.AuthenticatedUser;
import chungbuk.cityfarmerplus.common.web.PageResponse;
import chungbuk.cityfarmerplus.work.dto.AttendanceRequest;
import chungbuk.cityfarmerplus.work.dto.WorkAssignmentResponse;
import chungbuk.cityfarmerplus.work.service.WorkAssignmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/farm/work-assignments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('FARM')")
@Validated
public class FarmWorkAssignmentController {

    private final WorkAssignmentService service;

    @GetMapping
    public PageResponse<WorkAssignmentResponse> getMine(
            Authentication authentication,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return service.getFarmAssignments(AuthenticatedUser.id(authentication), page, size);
    }

    @PutMapping("/{assignmentId}/attendance")
    public WorkAssignmentResponse recordAttendance(
            Authentication authentication,
            @PathVariable Long assignmentId,
            @Valid @RequestBody AttendanceRequest request
    ) {
        return service.recordAttendance(
                AuthenticatedUser.id(authentication), assignmentId, request.status());
    }

    @PostMapping("/{assignmentId}/complete")
    public WorkAssignmentResponse complete(
            Authentication authentication,
            @PathVariable Long assignmentId
    ) {
        return service.completeByFarm(AuthenticatedUser.id(authentication), assignmentId);
    }
}
