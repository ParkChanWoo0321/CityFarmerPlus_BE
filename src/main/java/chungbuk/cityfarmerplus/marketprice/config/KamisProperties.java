package chungbuk.cityfarmerplus.marketprice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.kamis")
public record KamisProperties(
        String baseUrl,
        String certKey,
        String certId,
        Duration connectTimeout,
        Duration responseTimeout,
        Duration cacheTtl,
        Duration staleTtl,
        Duration failureBackoff
) {

    private static final String DEFAULT_BASE_URL =
            "https://www.kamis.or.kr/service/price/xml.do";

    public KamisProperties {
        baseUrl = normalize(baseUrl, DEFAULT_BASE_URL);
        certKey = normalize(certKey, "");
        certId = normalize(certId, "");
        connectTimeout = positive(connectTimeout, Duration.ofSeconds(2));
        responseTimeout = positive(responseTimeout, Duration.ofSeconds(5));
        cacheTtl = positive(cacheTtl, Duration.ofHours(1));
        staleTtl = positive(staleTtl, Duration.ofHours(24));
        failureBackoff = positive(failureBackoff, Duration.ofSeconds(30));

        if (!baseUrl.startsWith("https://")) {
            throw new IllegalArgumentException(
                    "KAMIS_BASE_URL은 HTTPS URL이어야 합니다."
            );
        }
    }

    public boolean configured() {
        return !certKey.isBlank() && !certId.isBlank();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static Duration positive(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative()
                ? fallback
                : value;
    }
}
