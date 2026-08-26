package chungbuk.cityfarmerplus.admin.work.controller;

import chungbuk.cityfarmerplus.admin.work.dto.AttendanceCorrectionRequest;
import chungbuk.cityfarmerplus.admin.work.dto.WorkAssignmentCorrectionResponse;
import chungbuk.cityfarmerplus.admin.work.service.AdminWorkAssignmentService;
import chungbuk.cityfarmerplus.common.web.AuthenticatedUser;
import chungbuk.cityfarmerplus.work.dto.WorkAssignmentResponse;
import chungbuk.cityfarmerplus.work.entity.WorkAssignment;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
@RequestMapping("/api/admin/work-assignments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CENTER_ADMIN')")
public class AdminWorkAssignmentController {

    private final AdminWorkAssignmentService workAssignmentService;

    @GetMapping
    public ResponseEntity<Page<WorkAssignmentResponse>> list(
            Authentication authentication,
            @RequestParam(required = false) WorkAssignment.WorkStatus status,
            Pageable pageable
    ) {
        return ResponseEntity.ok(workAssignmentService.list(
                AuthenticatedUser.id(authentication),
                status,
                pageable
        ));
    }

    @GetMapping("/{assignmentId}")
    public ResponseEntity<WorkAssignmentResponse> getDetail(
            Authentication authentication,
            @PathVariable Long assignmentId
    ) {
        return ResponseEntity.ok(workAssignmentService.getDetail(
                AuthenticatedUser.id(authentication),
                assignmentId
        ));
    }

    @PostMapping("/{assignmentId}/attendance-correction")
    public ResponseEntity<WorkAssignmentCorrectionResponse> correctAttendance(
            Authentication authentication,
            @PathVariable Long assignmentId,
            @Valid @RequestBody AttendanceCorrectionRequest request
    ) {
        return ResponseEntity.ok(workAssignmentService.correctAttendance(
                AuthenticatedUser.id(authentication),
                assignmentId,
                request
        ));
    }

    @GetMapping("/{assignmentId}/correction-history")
    public ResponseEntity<List<WorkAssignmentCorrectionResponse>> getCorrectionHistory(
            Authentication authentication,
            @PathVariable Long assignmentId
    ) {
        return ResponseEntity.ok(workAssignmentService.getCorrectionHistory(
                AuthenticatedUser.id(authentication),
                assignmentId
        ));
    }
}
