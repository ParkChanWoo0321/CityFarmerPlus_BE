package chungbuk.cityfarmerplus.auth.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AuthException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private AuthException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static AuthException duplicateLoginId() {
        return new AuthException(
                HttpStatus.CONFLICT,
                "DUPLICATE_LOGIN_ID",
                "이미 사용 중인 아이디입니다."
        );
    }

    public static AuthException invalidCredentials() {
        return new AuthException(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                "아이디 또는 비밀번호가 일치하지 않습니다."
        );
    }

    public static AuthException inactiveAccount() {
        return new AuthException(
                HttpStatus.FORBIDDEN,
                "INACTIVE_ACCOUNT",
                "현재 사용할 수 없는 계정입니다."
        );
    }

    public static AuthException userNotFound() {
        return new AuthException(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "사용자를 찾을 수 없습니다."
        );
    }

    public static AuthException managerSignupNotAllowed() {
        return new AuthException(
                HttpStatus.BAD_REQUEST,
                "MANAGER_SIGNUP_NOT_ALLOWED",
                "담당자는 공개 회원가입으로 생성할 수 없습니다."
        );
    }

    public static AuthException invalidAuthentication() {
        return new AuthException(
                HttpStatus.UNAUTHORIZED,
                "INVALID_AUTHENTICATION",
                "인증 정보가 올바르지 않습니다."
        );
    }

    public static AuthException adminProvisioningDisabled() {
        return new AuthException(
                HttpStatus.FORBIDDEN,
                "PROVISIONING_DISABLED",
                "담당자 계정 발급 기능이 비활성화되어 있습니다."
        );
    }

    public static AuthException invalidAdminProvisioningKey() {
        return new AuthException(
                HttpStatus.UNAUTHORIZED,
                "INVALID_PROVISIONING_KEY",
                "담당자 계정 발급 키가 올바르지 않습니다."
        );
    }

    public static AuthException invalidPassword() {
        return new AuthException(
                HttpStatus.UNAUTHORIZED,
                "INVALID_PASSWORD",
                "비밀번호가 일치하지 않습니다."
        );
    }

    public static AuthException withdrawalNotAllowed() {
        return new AuthException(
                HttpStatus.CONFLICT,
                "ACCOUNT_WITHDRAWAL_NOT_ALLOWED",
                "현재 계정 상태에서는 탈퇴할 수 없습니다."
        );
    }
}
