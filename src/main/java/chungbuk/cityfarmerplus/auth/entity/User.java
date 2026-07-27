package chungbuk.cityfarmerplus.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_users_login_id",
                columnNames = "login_id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", nullable = false, length = 30)
    private String loginId;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false, length = 30)
    private UserType userType;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    private AccountStatus accountStatus;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static User register(
            String loginId,
            String encodedPassword,
            String name,
            UserType userType
    ) {
        if (userType == UserType.CENTER_ADMIN) {
            throw new IllegalArgumentException("담당자는 공개 회원가입으로 생성할 수 없습니다.");
        }

        User user = new User();
        user.loginId = loginId;
        user.password = encodedPassword;
        user.name = name;
        user.userType = userType;
        user.accountStatus = AccountStatus.ACTIVE;
        return user;
    }

    public boolean isActive() {
        return accountStatus == AccountStatus.ACTIVE;
    }

    public enum UserType {
        URBAN_FARMER,
        FARM,
        CENTER_ADMIN
    }

    public enum AccountStatus {
        ACTIVE,
        SUSPENDED,
        WITHDRAWN
    }
}
