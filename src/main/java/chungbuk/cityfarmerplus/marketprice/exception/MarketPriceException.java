package chungbuk.cityfarmerplus.marketprice.exception;

import chungbuk.cityfarmerplus.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class MarketPriceException extends DomainException {

    private MarketPriceException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public static MarketPriceException copyOf(MarketPriceException exception) {
        return new MarketPriceException(
                exception.getStatus(),
                exception.getCode(),
                exception.getMessage()
        );
    }

    public static MarketPriceException configurationMissing() {
        return new MarketPriceException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "KAMIS_CONFIGURATION_MISSING",
                "농산물 가격정보 인증 설정이 완료되지 않았습니다."
        );
    }

    public static MarketPriceException authenticationFailed() {
        return new MarketPriceException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "KAMIS_AUTHENTICATION_FAILED",
                "농산물 가격정보 인증에 실패했습니다."
        );
    }

    public static MarketPriceException rejectedRequest() {
        return new MarketPriceException(
                HttpStatus.BAD_GATEWAY,
                "KAMIS_REJECTED_REQUEST",
                "농산물 가격정보 제공자가 요청을 거부했습니다."
        );
    }

    public static MarketPriceException invalidResponse() {
        return new MarketPriceException(
                HttpStatus.BAD_GATEWAY,
                "KAMIS_INVALID_RESPONSE",
                "농산물 가격정보 응답 형식이 올바르지 않습니다."
        );
    }

    public static MarketPriceException unavailable() {
        return new MarketPriceException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "MARKET_PRICE_UNAVAILABLE",
                "농산물 가격정보를 일시적으로 불러올 수 없습니다."
        );
    }
}
