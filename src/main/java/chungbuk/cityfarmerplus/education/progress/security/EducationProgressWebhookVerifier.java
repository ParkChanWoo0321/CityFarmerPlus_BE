package chungbuk.cityfarmerplus.education.progress.security;

import chungbuk.cityfarmerplus.common.exception.DomainException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class EducationProgressWebhookVerifier {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";

    private final byte[] secret;
    private final Duration tolerance;
    private final Clock clock;

    @Autowired
    public EducationProgressWebhookVerifier(
            @Value("${app.education.progress-webhook-secret:}") String secret,
            @Value("${app.education.progress-webhook-tolerance:5m}") Duration tolerance
    ) {
        this(secret, tolerance, Clock.systemUTC());
    }

    EducationProgressWebhookVerifier(String secret, Duration tolerance, Clock clock) {
        this.secret = secret == null
                ? new byte[0]
                : secret.getBytes(StandardCharsets.UTF_8);
        if (this.secret.length > 0 && this.secret.length < 32) {
            throw new IllegalStateException(
                    "교육 진도 웹훅 비밀키는 UTF-8 기준 32바이트 이상이어야 합니다."
            );
        }
        if (tolerance == null || tolerance.isZero() || tolerance.isNegative()) {
            throw new IllegalStateException(
                    "교육 진도 웹훅 서명 시각 허용 오차는 0보다 커야 합니다."
            );
        }
        this.tolerance = tolerance;
        this.clock = clock;
    }

    public String verify(
            String timestampHeader,
            String signatureHeader,
            byte[] requestBody
    ) {
        if (secret.length == 0) {
            throw new DomainException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "EDUCATION_PROGRESS_WEBHOOK_DISABLED",
                    "교육 진도 연동이 설정되지 않았습니다."
            );
        }

        Instant signedAt = parseTimestamp(timestampHeader);
        Instant now = clock.instant();
        if (signedAt.isBefore(now.minus(tolerance))
                || signedAt.isAfter(now.plus(tolerance))) {
            throw unauthorized("교육 진도 요청의 서명 시각이 허용 범위를 벗어났습니다.");
        }

        String normalizedSignature = signatureHeader == null
                ? ""
                : signatureHeader.trim().toLowerCase(Locale.ROOT);
        if (!normalizedSignature.matches("^sha256=[0-9a-f]{64}$")) {
            throw unauthorized("교육 진도 요청 서명 형식이 올바르지 않습니다.");
        }

        byte[] signedPayload = signedPayload(timestampHeader, requestBody);
        String expected = SIGNATURE_PREFIX + hmacSha256(signedPayload);
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                normalizedSignature.getBytes(StandardCharsets.US_ASCII)
        )) {
            throw unauthorized("교육 진도 요청 서명이 올바르지 않습니다.");
        }
        return sha256(requestBody);
    }

    private Instant parseTimestamp(String value) {
        try {
            return Instant.ofEpochSecond(Long.parseLong(value));
        } catch (DateTimeException | NumberFormatException exception) {
            throw unauthorized("교육 진도 요청 서명 시각 형식이 올바르지 않습니다.");
        }
    }

    private byte[] signedPayload(String timestampHeader, byte[] requestBody) {
        byte[] prefix = (timestampHeader + ".").getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[prefix.length + requestBody.length];
        System.arraycopy(prefix, 0, result, 0, prefix.length);
        System.arraycopy(requestBody, 0, result, prefix.length, requestBody.length);
        return result;
    }

    private String hmacSha256(byte[] value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(value));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("교육 진도 요청 서명을 계산할 수 없습니다.", exception);
        }
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value)
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("교육 진도 요청 해시를 계산할 수 없습니다.", exception);
        }
    }

    private DomainException unauthorized(String message) {
        return new DomainException(
                HttpStatus.UNAUTHORIZED,
                "INVALID_EDUCATION_PROGRESS_SIGNATURE",
                message
        );
    }
}
