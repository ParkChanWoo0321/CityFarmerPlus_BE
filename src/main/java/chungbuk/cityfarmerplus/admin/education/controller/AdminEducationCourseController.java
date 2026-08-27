package chungbuk.cityfarmerplus.admin.education.controller;

import chungbuk.cityfarmerplus.admin.education.dto.EducationCourseRequest;
import chungbuk.cityfarmerplus.admin.education.service.AdminEducationCourseService;
import chungbuk.cityfarmerplus.education.dto.EducationCourseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/education/courses")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CENTER_ADMIN')")
public class AdminEducationCourseController {

    private final AdminEducationCourseService courseService;

    @PostMapping
    public ResponseEntity<EducationCourseResponse> create(
            @Valid @RequestBody EducationCourseRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(courseService.create(request));
    }

    @PatchMapping("/{courseId}")
    public ResponseEntity<EducationCourseResponse> update(
            @PathVariable Long courseId,
            @Valid @RequestBody EducationCourseRequest request
    ) {
        return ResponseEntity.ok(courseService.update(courseId, request));
    }

    @PostMapping("/{courseId}/deactivate")
    public ResponseEntity<EducationCourseResponse> deactivate(
            @PathVariable Long courseId
    ) {
        return ResponseEntity.ok(courseService.deactivate(courseId));
    }
}
