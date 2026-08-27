package chungbuk.cityfarmerplus.admin.education.service;

import chungbuk.cityfarmerplus.admin.education.dto.EducationCourseRequest;
import chungbuk.cityfarmerplus.common.exception.DomainException;
import chungbuk.cityfarmerplus.education.dto.EducationCourseResponse;
import chungbuk.cityfarmerplus.education.entity.EducationCourse;
import chungbuk.cityfarmerplus.education.repository.EducationCourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminEducationCourseService {

    private final EducationCourseRepository courseRepository;

    @Transactional
    public EducationCourseResponse create(EducationCourseRequest request) {
        EducationCourse course = EducationCourse.create(
                request.title(),
                request.description(),
                request.requiredHours(),
                request.externalApplicationUrl(),
                request.mandatory(),
                request.displayOrder()
        );
        return EducationCourseResponse.from(courseRepository.save(course));
    }

    @Transactional
    public EducationCourseResponse update(Long courseId, EducationCourseRequest request) {
        EducationCourse course = getCourse(courseId);
        course.update(
                request.title(),
                request.description(),
                request.requiredHours(),
                request.externalApplicationUrl(),
                request.mandatory(),
                request.displayOrder()
        );
        return EducationCourseResponse.from(course);
    }

    @Transactional
    public EducationCourseResponse deactivate(Long courseId) {
        EducationCourse course = getCourse(courseId);
        course.deactivate();
        return EducationCourseResponse.from(course);
    }

    private EducationCourse getCourse(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(this::notFound);
    }

    private DomainException notFound() {
        return new DomainException(
                HttpStatus.NOT_FOUND,
                "EDUCATION_COURSE_NOT_FOUND",
                "교육 과정을 찾을 수 없습니다."
        );
    }
}
