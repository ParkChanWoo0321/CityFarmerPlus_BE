package chungbuk.cityfarmerplus.education.progress.service;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.repository.UserRepository;
import chungbuk.cityfarmerplus.common.exception.DomainException;
import chungbuk.cityfarmerplus.education.entity.EducationCourse;
import chungbuk.cityfarmerplus.education.progress.dto.EducationEnrollmentResponse;
import chungbuk.cityfarmerplus.education.progress.dto.EducationProgressEventRequest;
import chungbuk.cityfarmerplus.education.progress.entity.EducationEnrollment;
import chungbuk.cityfarmerplus.education.progress.entity.EducationProgressEvent;
import chungbuk.cityfarmerplus.education.progress.repository.EducationEnrollmentRepository;
import chungbuk.cityfarmerplus.education.progress.repository.EducationProgressEventRepository;
import chungbuk.cityfarmerplus.education.repository.EducationCourseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Service
public class EducationProgressEventService {

    private static final Duration MAX_FUTURE_EVENT_SKEW = Duration.ofMinutes(5);

    private final UserRepository userRepository;
    private final EducationCourseRepository courseRepository;
    private final EducationEnrollmentRepository enrollmentRepository;
    private final EducationProgressEventRepository eventRepository;
    private final Clock clock;

    @Autowired
    public EducationProgressEventService(
            UserRepository userRepository,
            EducationCourseRepository courseRepository,
            EducationEnrollmentRepository enrollmentRepository,
            EducationProgressEventRepository eventRepository
    ) {
        this(
                userRepository,
                courseRepository,
                enrollmentRepository,
                eventRepository,
                Clock.systemUTC()
        );
    }

    EducationProgressEventService(
            UserRepository userRepository,
            EducationCourseRepository courseRepository,
            EducationEnrollmentRepository enrollmentRepository,
            EducationProgressEventRepository eventRepository,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.eventRepository = eventRepository;
        this.clock = clock;
    }

    @Transactional
    public EducationEnrollmentResponse ingest(
            EducationProgressEventRequest request,
            String payloadSha256
    ) {
        String provider = request.provider().trim().toUpperCase(Locale.ROOT);
        String eventId = request.eventId().trim();
        String externalEnrollmentId = request.externalEnrollmentId().trim();

        EducationProgressEvent duplicate = eventRepository
                .findByProviderAndProviderEventId(provider, eventId)
                .orElse(null);
        if (duplicate != null) {
            if (!duplicate.getPayloadSha256().equals(payloadSha256)) {
                throw conflict(
                        "EDUCATION_PROGRESS_EVENT_CONFLICT",
                        "같은 교육 진도 이벤트 ID가 서로 다른 내용으로 전달되었습니다."
                );
            }
            return EducationEnrollmentResponse.from(duplicate.getEnrollment());
        }

        Instant receivedAt = clock.instant();
        validateRequest(request, receivedAt);
        User urbanFarmer = userRepository.findByIdForUpdate(request.urbanFarmerId())
                .filter(user -> user.getUserType() == User.UserType.URBAN_FARMER)
                .filter(User::isActive)
                .orElseThrow(() -> new DomainException(
                        HttpStatus.NOT_FOUND,
                        "ACTIVE_URBAN_FARMER_NOT_FOUND",
                        "활성 도시농부 회원을 찾을 수 없습니다."
                ));
        EducationCourse course = courseRepository
                .findByIdAndActiveTrue(request.courseId())
                .orElseThrow(() -> new DomainException(
                        HttpStatus.NOT_FOUND,
                        "ACTIVE_EDUCATION_COURSE_NOT_FOUND",
                        "활성 교육 과정을 찾을 수 없습니다."
                ));
        validateCourseDuration(request, course);

        EducationEnrollment enrollment = enrollmentRepository
                .findForUpdate(request.urbanFarmerId(), request.courseId())
                .orElse(null);
        boolean applied;
        if (enrollment == null) {
            ensureExternalEnrollmentAvailable(provider, externalEnrollmentId);
            enrollment = EducationEnrollment.create(
                    urbanFarmer,
                    course,
                    provider,
                    externalEnrollmentId,
                    request.totalMinutes(),
                    request.completedMinutes(),
                    request.occurredAt(),
                    receivedAt
            );
            enrollmentRepository.save(enrollment);
            applied = true;
        } else {
            requireSameExternalEnrollment(enrollment, provider, externalEnrollmentId);
            try {
                applied = enrollment.applyProgress(
                        request.totalMinutes(),
                        request.completedMinutes(),
                        request.occurredAt(),
                        receivedAt
                );
            } catch (IllegalStateException exception) {
                throw conflict(
                        "EDUCATION_PROGRESS_REGRESSION",
                        exception.getMessage()
                );
            }
        }

        eventRepository.save(EducationProgressEvent.create(
                enrollment,
                provider,
                eventId,
                payloadSha256,
                request.totalMinutes(),
                request.completedMinutes(),
                applied,
                request.occurredAt(),
                receivedAt
        ));
        return EducationEnrollmentResponse.from(enrollment);
    }

    private void validateRequest(
            EducationProgressEventRequest request,
            Instant receivedAt
    ) {
        if (request.completedMinutes() > request.totalMinutes()) {
            throw new DomainException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_EDUCATION_PROGRESS",
                    "현재 수강 시간은 전체 교육 시간을 초과할 수 없습니다."
            );
        }
        if (request.occurredAt().isAfter(receivedAt.plus(MAX_FUTURE_EVENT_SKEW))) {
            throw new DomainException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_EDUCATION_PROGRESS_TIME",
                    "교육 진도 발생 시각은 현재 시각보다 5분 넘게 미래일 수 없습니다."
            );
        }
    }

    private void ensureExternalEnrollmentAvailable(
            String provider,
            String externalEnrollmentId
    ) {
        if (enrollmentRepository
                .findByProviderAndExternalEnrollmentId(provider, externalEnrollmentId)
                .isPresent()) {
            throw conflict(
                    "EDUCATION_ENROLLMENT_CONFLICT",
                    "외부 수강 등록 ID가 이미 다른 교육 등록에 연결되어 있습니다."
            );
        }
    }

    private void validateCourseDuration(
            EducationProgressEventRequest request,
            EducationCourse course
    ) {
        int requiredMinutes = Math.multiplyExact(course.getRequiredHours(), 60);
        if (request.totalMinutes() < requiredMinutes) {
            throw new DomainException(
                    HttpStatus.BAD_REQUEST,
                    "INSUFFICIENT_EDUCATION_PROGRESS_DURATION",
                    "전체 교육 시간은 과정의 필수 교육 시간보다 짧을 수 없습니다."
            );
        }
    }

    private void requireSameExternalEnrollment(
            EducationEnrollment enrollment,
            String provider,
            String externalEnrollmentId
    ) {
        if (!enrollment.getProvider().equals(provider)
                || !enrollment.getExternalEnrollmentId().equals(externalEnrollmentId)) {
            throw conflict(
                    "EDUCATION_ENROLLMENT_CONFLICT",
                    "회원과 과정에 이미 다른 외부 수강 등록이 연결되어 있습니다."
            );
        }
    }

    private DomainException conflict(String code, String message) {
        return new DomainException(HttpStatus.CONFLICT, code, message);
    }
}
