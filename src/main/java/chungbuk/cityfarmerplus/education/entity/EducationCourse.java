package chungbuk.cityfarmerplus.education.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "education_courses",
        indexes = @Index(
                name = "idx_education_course_active_order",
                columnList = "active, display_order"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EducationCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(name = "required_hours", nullable = false)
    private int requiredHours;

    @Column(name = "external_application_url", length = 500)
    private String externalApplicationUrl;

    @Column(nullable = false)
    private boolean mandatory;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static EducationCourse create(
            String title,
            String description,
            int requiredHours,
            String externalApplicationUrl,
            boolean mandatory,
            int displayOrder
    ) {
        validate(requiredHours, displayOrder);
        EducationCourse course = new EducationCourse();
        course.title = title;
        course.description = description;
        course.requiredHours = requiredHours;
        course.externalApplicationUrl = externalApplicationUrl;
        course.mandatory = mandatory;
        course.active = true;
        course.displayOrder = displayOrder;
        return course;
    }

    public void update(
            String title,
            String description,
            int requiredHours,
            String externalApplicationUrl,
            boolean mandatory,
            int displayOrder
    ) {
        validate(requiredHours, displayOrder);
        this.title = title;
        this.description = description;
        this.requiredHours = requiredHours;
        this.externalApplicationUrl = externalApplicationUrl;
        this.mandatory = mandatory;
        this.displayOrder = displayOrder;
    }

    public void deactivate() {
        active = false;
    }

    private static void validate(int requiredHours, int displayOrder) {
        if (requiredHours < 1) {
            throw new IllegalArgumentException("교육 시간은 1시간 이상이어야 합니다.");
        }
        if (displayOrder < 0) {
            throw new IllegalArgumentException("표시 순서는 0 이상이어야 합니다.");
        }
    }
}
