package chungbuk.cityfarmerplus.education.controller;

import chungbuk.cityfarmerplus.education.dto.EducationCourseResponse;
import chungbuk.cityfarmerplus.education.service.EducationCourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/education/courses")
@RequiredArgsConstructor
public class EducationCourseController {

    private final EducationCourseService courseService;

    @GetMapping
    public ResponseEntity<List<EducationCourseResponse>> getActiveCourses() {
        return ResponseEntity.ok(courseService.getActiveCourses());
    }
}
