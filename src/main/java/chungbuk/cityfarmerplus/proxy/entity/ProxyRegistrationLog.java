package chungbuk.cityfarmerplus.proxy.entity;

import chungbuk.cityfarmerplus.auth.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "proxy_registration_logs",
        indexes = @Index(
                name = "idx_proxy_registration_logs_target_user",
                columnList = "target_user_id,processed_at"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProxyRegistrationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_user_id", nullable = false, updatable = false)
    private User admin;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_user_id", nullable = false, updatable = false)
    private User targetUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 30, updatable = false)
    private ActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30, updatable = false)
    private TargetType targetType;

    @Column(name = "target_object_id", nullable = false, updatable = false)
    private Long targetObjectId;

    @Column(length = 1000, updatable = false)
    private String reason;

    @CreationTimestamp
    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    public static ProxyRegistrationLog record(
            User admin,
            User targetUser,
            ActionType actionType,
            TargetType targetType,
            Long targetObjectId,
            String reason
    ) {
        if (admin == null
                || admin.getUserType() != User.UserType.CENTER_ADMIN
                || !admin.isActive()) {
            throw new IllegalArgumentException(
                    "활성 상태인 담당자만 대리 접수 이력을 기록할 수 있습니다."
            );
        }
        if (targetUser == null) {
            throw new IllegalArgumentException("대리 접수 대상 회원은 필수입니다.");
        }
        if (actionType == null) {
            throw new IllegalArgumentException("작업 종류는 필수입니다.");
        }
        if (targetType == null) {
            throw new IllegalArgumentException("대상 객체 종류는 필수입니다.");
        }
        if (targetObjectId == null) {
            throw new IllegalArgumentException("대상 객체 ID는 필수입니다.");
        }
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isBlank()) {
            throw new IllegalArgumentException("작업 사유는 필수입니다.");
        }
        if (normalizedReason.length() > 1000) {
            throw new IllegalArgumentException("작업 사유는 1000자 이하여야 합니다.");
        }
        ProxyRegistrationLog log = new ProxyRegistrationLog();
        log.admin = admin;
        log.targetUser = targetUser;
        log.actionType = actionType;
        log.targetType = targetType;
        log.targetObjectId = targetObjectId;
        log.reason = normalizedReason;
        return log;
    }

    public enum ActionType {
        URBAN_FARMER_ACCOUNT_CREATED,
        URBAN_FARMER_PROFILE_REGISTERED,
        WORK_PREFERENCE_REGISTERED,
        PARTICIPATION_APPLICATION_CREATED,
        PARTICIPATION_APPLICATION_SUBMITTED,
        FARM_ACCOUNT_CREATED,
        FARM_PROFILE_REGISTERED,
        EDUCATION_SUBMISSION_REGISTERED,
        FARM_OWNERSHIP_SUBMISSION_REGISTERED,
        JOB_POSTING_DRAFT_CREATED
    }

    public enum TargetType {
        USER,
        URBAN_FARMER_PROFILE,
        WORK_PREFERENCE,
        PARTICIPATION_APPLICATION,
        FARM_PROFILE,
        EDUCATION_SUBMISSION,
        FARM_OWNERSHIP_SUBMISSION,
        JOB_POSTING
    }
}
