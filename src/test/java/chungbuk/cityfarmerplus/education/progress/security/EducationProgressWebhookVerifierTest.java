package chungbuk.cityfarmerplus.education.progress.security;

import chungbuk.cityfarmerplus.common.exception.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EducationProgressWebhookVerifierTest {

    private static final String SECRET = "test-webhook-secret-at-least-32-bytes";
    private static final Instant NOW = Instant.parse("2026-08-28T00:00:00Z");

    @Test
    void acceptsHmacOfExactTimestampAndRawBody() throws Exception {
        EducationProgressWebhookVerifier verifier = verifier(SECRET);
        String timestamp = Long.toString(NOW.getEpochSecond());
        byte[] body = "{\"eventId\":\"evt-1\"}".getBytes(StandardCharsets.UTF_8);

        String digest = verifier.verify(timestamp, signature(timestamp, body), body);

        assertThat(digest).hasSize(64);
    }

    @Test
    void rejectsChangedBodyAndExpiredTimestamp() throws Exception {
        EducationProgressWebhookVerifier verifier = verifier(SECRET);
        String currentTimestamp = Long.toString(NOW.getEpochSecond());
        byte[] original = "{}".getBytes(StandardCharsets.UTF_8);
        byte[] changed = "{ }".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> verifier.verify(
                currentTimestamp,
                signature(currentTimestamp, original),
                changed
        )).isInstanceOfSatisfying(DomainException.class, exception -> {
            assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(exception.getCode())
                    .isEqualTo("INVALID_EDUCATION_PROGRESS_SIGNATURE");
        });

        String oldTimestamp = Long.toString(NOW.minus(Duration.ofMinutes(6)).getEpochSecond());
        assertThatThrownBy(() -> verifier.verify(
                oldTimestamp,
                signature(oldTimestamp, original),
                original
        )).isInstanceOfSatisfying(DomainException.class, exception ->
                assertThat(exception.getCode())
                        .isEqualTo("INVALID_EDUCATION_PROGRESS_SIGNATURE"));
    }

    @Test
    void blankSecretKeepsPublicRouteDisabled() {
        EducationProgressWebhookVerifier verifier = verifier("");

        assertThatThrownBy(() -> verifier.verify("0", "sha256=" + "0".repeat(64), new byte[0]))
                .isInstanceOfSatisfying(DomainException.class, exception -> {
                    assertThat(exception.getStatus())
                            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.getCode())
                            .isEqualTo("EDUCATION_PROGRESS_WEBHOOK_DISABLED");
                });
    }

    @Test
    void configuredSecretMustHaveAtLeastThirtyTwoBytes() {
        assertThatThrownBy(() -> verifier("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32바이트");
    }

    private EducationProgressWebhookVerifier verifier(String secret) {
        return new EducationProgressWebhookVerifier(
                secret,
                Duration.ofMinutes(5),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private String signature(String timestamp, byte[] body) throws Exception {
        byte[] prefix = (timestamp + ".").getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[prefix.length + body.length];
        System.arraycopy(prefix, 0, payload, 0, prefix.length);
        System.arraycopy(body, 0, payload, prefix.length, body.length);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload));
    }
}
