package chungbuk.cityfarmerplus.education.progress.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
        name = "education_progress_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_education_progress_event_provider_event",
                columnNames = {"provider", "provider_event_id"}
        ),
        indexes = {
                @Index(
                        name = "idx_education_progress_event_enrollment_time",
                        columnList = "education_enrollment_id, occurred_at"
                ),
                @Index(
                        name = "idx_education_progress_event_received",
                        columnList = "received_at"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EducationProgressEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "education_enrollment_id", nullable = false, updatable = false)
    private EducationEnrollment enrollment;

    @Column(nullable = false, length = 50, updatable = false)
    private String provider;

    @Column(name = "provider_event_id", nullable = false, length = 100, updatable = false)
    private String providerEventId;

    @Column(name = "payload_sha256", nullable = false, length = 64, updatable = false)
    private String payloadSha256;

    @Column(name = "total_minutes", nullable = false, updatable = false)
    private int totalMinutes;

    @Column(name = "completed_minutes", nullable = false, updatable = false)
    private int completedMinutes;

    @Column(nullable = false, updatable = false)
    private boolean applied;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    public static EducationProgressEvent create(
            EducationEnrollment enrollment,
            String provider,
            String providerEventId,
            String payloadSha256,
            int totalMinutes,
            int completedMinutes,
            boolean applied,
            Instant occurredAt,
            Instant receivedAt
    ) {
        EducationProgressEvent event = new EducationProgressEvent();
        event.enrollment = enrollment;
        event.provider = provider;
        event.providerEventId = providerEventId;
        event.payloadSha256 = payloadSha256;
        event.totalMinutes = totalMinutes;
        event.completedMinutes = completedMinutes;
        event.applied = applied;
        event.occurredAt = occurredAt;
        event.receivedAt = receivedAt;
        return event;
    }
}
