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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EducationProgressEventServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-28T00:05:00Z");
    private static final String PAYLOAD_SHA256 = "a".repeat(64);

    @Mock
    private UserRepository userRepository;

    @Mock
    private EducationCourseRepository courseRepository;

    @Mock
    private EducationEnrollmentRepository enrollmentRepository;

    @Mock
    private EducationProgressEventRepository eventRepository;

    private EducationProgressEventService service;
    private User urbanFarmer;
    private EducationCourse course;

    @BeforeEach
    void setUp() {
        service = new EducationProgressEventService(
                userRepository,
                courseRepository,
                enrollmentRepository,
                eventRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        urbanFarmer = User.register(
                "urban_21",
                "encoded",
                "도시농부",
                User.UserType.URBAN_FARMER
        );
        ReflectionTestUtils.setField(urbanFarmer, "id", 21L);
        course = EducationCourse.create(
                "도시농업 기초",
                "필수 교육",
                8,
                "https://example.com",
                true,
                1
        );
        ReflectionTestUtils.setField(course, "id", 1L);
    }

    @Test
    void createsEnrollmentAndImmutableEventFromFirstProgressUpdate() {
        stubNewEvent();
        when(enrollmentRepository.save(any(EducationEnrollment.class)))
                .thenAnswer(invocation -> {
                    EducationEnrollment enrollment = invocation.getArgument(0);
                    ReflectionTestUtils.setField(enrollment, "id", 100L);
                    return enrollment;
                });

        EducationEnrollmentResponse response = service.ingest(request(
                "evt-1",
                240,
                NOW.minusSeconds(30)
        ), PAYLOAD_SHA256);

        assertThat(response.enrollmentId()).isEqualTo(100L);
        assertThat(response.progressStatus())
                .isEqualTo(EducationEnrollment.ProgressStatus.IN_PROGRESS);
        assertThat(response.progressPercentage()).isEqualTo(50);
        ArgumentCaptor<EducationProgressEvent> eventCaptor =
                ArgumentCaptor.forClass(EducationProgressEvent.class);
        verify(eventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().isApplied()).isTrue();
        assertThat(eventCaptor.getValue().getPayloadSha256())
                .isEqualTo(PAYLOAD_SHA256);
    }

    @Test
    void exactDuplicateEventIsIdempotent() {
        EducationEnrollment enrollment = enrollment(240, NOW.minusSeconds(60));
        EducationProgressEvent event = EducationProgressEvent.create(
                enrollment,
                "CHUNGBUK_LMS",
                "evt-1",
                PAYLOAD_SHA256,
                480,
                240,
                true,
                NOW.minusSeconds(60),
                NOW.minusSeconds(59)
        );
        when(eventRepository.findByProviderAndProviderEventId("CHUNGBUK_LMS", "evt-1"))
                .thenReturn(Optional.of(event));

        EducationEnrollmentResponse response = service.ingest(request(
                "evt-1",
                240,
                NOW.minusSeconds(60)
        ), PAYLOAD_SHA256);

        assertThat(response.enrollmentId()).isEqualTo(100L);
        assertThat(response.completedMinutes()).isEqualTo(240);
        verifyNoInteractions(userRepository, courseRepository, enrollmentRepository);
    }

    @Test
    void sameEventIdWithDifferentPayloadIsRejected() {
        EducationEnrollment enrollment = enrollment(240, NOW.minusSeconds(60));
        EducationProgressEvent event = EducationProgressEvent.create(
                enrollment,
                "CHUNGBUK_LMS",
                "evt-1",
                "b".repeat(64),
                480,
                240,
                true,
                NOW.minusSeconds(60),
                NOW.minusSeconds(59)
        );
        when(eventRepository.findByProviderAndProviderEventId("CHUNGBUK_LMS", "evt-1"))
                .thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service.ingest(request(
                "evt-1",
                240,
                NOW.minusSeconds(60)
        ), PAYLOAD_SHA256)).isInstanceOfSatisfying(DomainException.class, exception -> {
            assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(exception.getCode())
                    .isEqualTo("EDUCATION_PROGRESS_EVENT_CONFLICT");
        });
    }

    @Test
    void staleEventIsStoredWithoutOverwritingCurrentProgress() {
        EducationEnrollment enrollment = enrollment(240, NOW.minusSeconds(60));
        stubExistingEvent(enrollment);

        EducationEnrollmentResponse response = service.ingest(request(
                "evt-old",
                120,
                NOW.minusSeconds(120)
        ), PAYLOAD_SHA256);

        assertThat(response.completedMinutes()).isEqualTo(240);
        ArgumentCaptor<EducationProgressEvent> eventCaptor =
                ArgumentCaptor.forClass(EducationProgressEvent.class);
        verify(eventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().isApplied()).isFalse();
    }

    @Test
    void newerEventCannotReduceStoredProgress() {
        EducationEnrollment enrollment = enrollment(240, NOW.minusSeconds(60));
        stubExistingEvent(enrollment);

        assertThatThrownBy(() -> service.ingest(request(
                "evt-regression",
                120,
                NOW.minusSeconds(30)
        ), PAYLOAD_SHA256)).isInstanceOfSatisfying(DomainException.class, exception -> {
            assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(exception.getCode()).isEqualTo("EDUCATION_PROGRESS_REGRESSION");
        });
    }

    @Test
    void providerTotalCannotBeShorterThanCourseRequirement() {
        when(eventRepository.findByProviderAndProviderEventId(
                "CHUNGBUK_LMS",
                "evt-short"
        )).thenReturn(Optional.empty());
        when(userRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(urbanFarmer));
        when(courseRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(course));
        EducationProgressEventRequest request = new EducationProgressEventRequest(
                "CHUNGBUK_LMS",
                "evt-short",
                "enrollment-21-1",
                21L,
                1L,
                479,
                240,
                NOW.minusSeconds(30)
        );

        assertThatThrownBy(() -> service.ingest(request, PAYLOAD_SHA256))
                .isInstanceOfSatisfying(DomainException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getCode())
                            .isEqualTo("INSUFFICIENT_EDUCATION_PROGRESS_DURATION");
                });
        verifyNoInteractions(enrollmentRepository);
    }

    private void stubNewEvent() {
        when(eventRepository.findByProviderAndProviderEventId(
                "CHUNGBUK_LMS",
                "evt-1"
        )).thenReturn(Optional.empty());
        when(userRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(urbanFarmer));
        when(courseRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.findForUpdate(21L, 1L)).thenReturn(Optional.empty());
        when(enrollmentRepository.findByProviderAndExternalEnrollmentId(
                "CHUNGBUK_LMS",
                "enrollment-21-1"
        )).thenReturn(Optional.empty());
    }

    private void stubExistingEvent(EducationEnrollment enrollment) {
        when(eventRepository.findByProviderAndProviderEventId(
                eq("CHUNGBUK_LMS"),
                org.mockito.ArgumentMatchers.anyString()
        )).thenReturn(Optional.empty());
        when(userRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(urbanFarmer));
        when(courseRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.findForUpdate(21L, 1L))
                .thenReturn(Optional.of(enrollment));
    }

    private EducationEnrollment enrollment(int completedMinutes, Instant occurredAt) {
        EducationEnrollment enrollment = EducationEnrollment.create(
                urbanFarmer,
                course,
                "CHUNGBUK_LMS",
                "enrollment-21-1",
                480,
                completedMinutes,
                occurredAt,
                occurredAt.plusSeconds(1)
        );
        ReflectionTestUtils.setField(enrollment, "id", 100L);
        return enrollment;
    }

    private EducationProgressEventRequest request(
            String eventId,
            int completedMinutes,
            Instant occurredAt
    ) {
        return new EducationProgressEventRequest(
                "chungbuk_lms",
                eventId,
                "enrollment-21-1",
                21L,
                1L,
                480,
                completedMinutes,
                occurredAt
        );
    }
}
