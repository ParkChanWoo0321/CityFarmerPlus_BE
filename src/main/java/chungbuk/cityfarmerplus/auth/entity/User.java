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
import java.time.LocalDate;

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

    @Column(name = "phone_number", length = 11)
    private String phoneNumber;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(length = 255)
    private String address;

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

        return create(loginId, encodedPassword, name, userType);
    }

    public static User registerCenterAdmin(
            String loginId,
            String encodedPassword,
            String name
    ) {
        return create(loginId, encodedPassword, name, UserType.CENTER_ADMIN);
    }

    private static User create(
            String loginId,
            String encodedPassword,
            String name,
            UserType userType
    ) {
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

    public boolean canWithdraw() {
        return accountStatus == AccountStatus.ACTIVE;
    }

    public void updateName(String name) {
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isBlank() || normalizedName.length() > 50) {
            throw new IllegalArgumentException("이름이 올바르지 않습니다.");
        }
        this.name = normalizedName;
    }

    public void updatePhoneNumber(String phoneNumber) {
        if (phoneNumber != null && !phoneNumber.matches("\\d{10,11}")) {
            throw new IllegalArgumentException("연락처는 숫자 10~11자리여야 합니다.");
        }
        this.phoneNumber = phoneNumber;
    }

    public void updateBirthDate(LocalDate birthDate) {
        if (birthDate != null && birthDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("생년월일은 미래일 수 없습니다.");
        }
        this.birthDate = birthDate;
    }

    public void updateAddress(String address) {
        String normalizedAddress = address == null ? null : address.trim();
        if (normalizedAddress != null && normalizedAddress.length() > 255) {
            throw new IllegalArgumentException("주소는 255자 이하여야 합니다.");
        }
        this.address = normalizedAddress == null || normalizedAddress.isBlank()
                ? null
                : normalizedAddress;
    }

    public void withdraw() {
        if (!canWithdraw()) {
            throw new IllegalStateException("현재 계정 상태에서는 탈퇴할 수 없습니다.");
        }
        accountStatus = AccountStatus.WITHDRAWN;
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
