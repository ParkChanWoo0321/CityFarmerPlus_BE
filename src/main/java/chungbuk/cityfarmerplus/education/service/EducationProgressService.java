package chungbuk.cityfarmerplus.education.service;

import chungbuk.cityfarmerplus.education.dto.EducationCertificationResponse;
import chungbuk.cityfarmerplus.education.dto.EducationCourseProgressResponse;
import chungbuk.cityfarmerplus.education.entity.EducationCertificateSubmission;
import chungbuk.cityfarmerplus.education.entity.EducationCertification;
import chungbuk.cityfarmerplus.education.entity.EducationCourse;
import chungbuk.cityfarmerplus.education.progress.entity.EducationEnrollment;
import chungbuk.cityfarmerplus.education.progress.repository.EducationEnrollmentRepository;
import chungbuk.cityfarmerplus.education.repository.EducationCertificateSubmissionRepository;
import chungbuk.cityfarmerplus.education.repository.EducationCourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EducationProgressService {

    private final EducationCourseRepository courseRepository;
    private final EducationCertificateSubmissionRepository submissionRepository;
    private final EducationEnrollmentRepository enrollmentRepository;
    private final EducationCertificationProgressCalculator calculator;

    public EducationCertificationResponse getProgress(
            Long userId,
            EducationCertification certification
    ) {
        List<EducationCourse> courses =
                courseRepository.findAllByActiveTrueOrderByDisplayOrderAscTitleAsc();
        List<EducationCertificateSubmission> submissions = certification == null
                ? List.of()
                : submissionRepository
                .findAllByCertificationIdOrderByAttemptNumberDesc(
                        certification.getId()
                );

        var result = calculator.calculate(courses, submissions);
        List<Long> courseIds = courses.stream()
                .map(EducationCourse::getId)
                .toList();
        Map<Long, EducationEnrollment> enrollmentByCourseId = courseIds.isEmpty()
                ? Map.of()
                : enrollmentRepository.findAllForProgress(userId, courseIds)
                .stream()
                .collect(Collectors.toMap(
                        enrollment -> enrollment.getCourse().getId(),
                        Function.identity()
                ));

        List<EducationCourseProgressResponse> progress = courses.stream()
                .map(course -> EducationCourseProgressResponse.from(
                        course,
                        result.latestSubmissionByCourse().get(course.getId()),
                        enrollmentByCourseId.get(course.getId())
                ))
                .toList();

        return EducationCertificationResponse.progress(
                userId,
                certification,
                result.status(),
                result.representativeApprovedSubmission() == null
                        ? null
                        : result.representativeApprovedSubmission().getId(),
                result.recognizedHours(),
                result.approvedAt(),
                result.eligible(),
                result.requiredCourseCount(),
                result.approvedRequiredCourseCount(),
                progress
        );
    }
}
