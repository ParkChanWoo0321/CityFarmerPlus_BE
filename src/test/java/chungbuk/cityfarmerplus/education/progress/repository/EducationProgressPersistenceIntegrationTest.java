package chungbuk.cityfarmerplus.education.progress.repository;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import chungbuk.cityfarmerplus.education.entity.EducationCourse;
import chungbuk.cityfarmerplus.education.progress.entity.EducationEnrollment;
import chungbuk.cityfarmerplus.education.progress.entity.EducationProgressEvent;
import chungbuk.cityfarmerplus.education.repository.EducationCourseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
class EducationProgressPersistenceIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EducationCourseRepository courseRepository;

    @Autowired
    private EducationEnrollmentRepository enrollmentRepository;

    @Autowired
    private EducationProgressEventRepository eventRepository;

    @Test
    void persistsEnrollmentAndAppendOnlyProviderEventWithRepositoryQueries() {
        User user = userRepository.save(User.register(
                "progress_user",
                "encoded",
                "도시농부",
                User.UserType.URBAN_FARMER
        ));
        EducationCourse course = courseRepository.save(EducationCourse.create(
                "실시간 진도 과정",
                "교육 진도 저장 테스트",
                8,
                "https://example.com/progress",
                true,
                1
        ));
        Instant occurredAt = Instant.parse("2026-08-28T00:00:00Z");
        EducationEnrollment enrollment = enrollmentRepository.save(
                EducationEnrollment.create(
                        user,
                        course,
                        "CHUNGBUK_LMS",
                        "enrollment-progress-user",
                        480,
                        240,
                        occurredAt,
                        occurredAt.plusSeconds(1)
                )
        );
        EducationProgressEvent event = eventRepository.save(
                EducationProgressEvent.create(
                        enrollment,
                        "CHUNGBUK_LMS",
                        "evt-progress-user-1",
                        "a".repeat(64),
                        480,
                        240,
                        true,
                        occurredAt,
                        occurredAt.plusSeconds(1)
                )
        );
        enrollmentRepository.flush();

        assertThat(enrollmentRepository.findAllForProgress(
                user.getId(),
                List.of(course.getId())
        )).containsExactly(enrollment);
        assertThat(enrollmentRepository.findForUpdate(user.getId(), course.getId()))
                .contains(enrollment);
        assertThat(enrollmentRepository.findByProviderAndExternalEnrollmentId(
                "CHUNGBUK_LMS",
                "enrollment-progress-user"
        )).contains(enrollment);
        assertThat(eventRepository.findByProviderAndProviderEventId(
                "CHUNGBUK_LMS",
                "evt-progress-user-1"
        )).contains(event);
    }
}
