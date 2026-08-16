package chungbuk.cityfarmerplus.education.service;

import chungbuk.cityfarmerplus.education.dto.EducationCourseResponse;
import chungbuk.cityfarmerplus.education.repository.EducationCourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EducationCourseService {

    private final EducationCourseRepository courseRepository;

    @Transactional(readOnly = true)
    public List<EducationCourseResponse> getActiveCourses() {
        return courseRepository.findAllByActiveTrueOrderByDisplayOrderAscTitleAsc()
                .stream()
                .map(EducationCourseResponse::from)
                .toList();
    }

}
