package chungbuk.cityfarmerplus.ai.support;

import chungbuk.cityfarmerplus.auth.entity.User;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "support_inquiries",
        indexes = @Index(name = "idx_support_inquiries_user_created", columnList = "user_id,created_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupportInquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(nullable = false, length = 1000, updatable = false)
    private String question;

    @Column(nullable = false, length = 50, updatable = false)
    private String category;

    @Column(nullable = false, length = 3000, updatable = false)
    private String answer;

    @Column(name = "official_confirmation_required", nullable = false, updatable = false)
    private boolean officialConfirmationRequired;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static SupportInquiry create(User user, String question, SupportAnswer answer) {
        SupportInquiry inquiry = new SupportInquiry();
        inquiry.user = user;
        inquiry.question = question;
        inquiry.category = answer.category();
        inquiry.answer = answer.answer();
        inquiry.officialConfirmationRequired = answer.officialConfirmationRequired();
        return inquiry;
    }
}
