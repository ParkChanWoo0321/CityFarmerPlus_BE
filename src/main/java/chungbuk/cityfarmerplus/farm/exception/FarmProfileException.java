package chungbuk.cityfarmerplus.farm.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class FarmProfileException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private FarmProfileException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static FarmProfileException farmRoleRequired() {
        return new FarmProfileException(
                HttpStatus.FORBIDDEN,
                "FARM_ROLE_REQUIRED",
                "농가 계정만 농가 프로필을 사용할 수 있습니다."
        );
    }

    public static FarmProfileException profileAlreadyExists() {
        return new FarmProfileException(
                HttpStatus.CONFLICT,
                "FARM_PROFILE_ALREADY_EXISTS",
                "이미 농가 프로필이 등록되어 있습니다."
        );
    }

    public static FarmProfileException profileNotFound() {
        return new FarmProfileException(
                HttpStatus.NOT_FOUND,
                "FARM_PROFILE_NOT_FOUND",
                "농가 프로필을 찾을 수 없습니다."
        );
    }

    public static FarmProfileException dataConflict() {
        return new FarmProfileException(
                HttpStatus.CONFLICT,
                "FARM_PROFILE_DATA_CONFLICT",
                "이미 등록된 농가 정보와 충돌합니다."
        );
    }

    public static FarmProfileException profileUpdateNotAllowed() {
        return new FarmProfileException(
                HttpStatus.CONFLICT,
                "FARM_PROFILE_UPDATE_NOT_ALLOWED",
                "현재 농가 프로필 상태에서는 기본 정보를 수정할 수 없습니다."
        );
    }

}
