package chungbuk.cityfarmerplus.education.repository;

import chungbuk.cityfarmerplus.education.entity.EducationCourse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EducationCourseRepository extends JpaRepository<EducationCourse, Long> {

    List<EducationCourse> findAllByActiveTrueOrderByDisplayOrderAscTitleAsc();

}
