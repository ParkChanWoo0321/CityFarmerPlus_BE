package chungbuk.cityfarmerplus.auth.jwt;

import chungbuk.cityfarmerplus.auth.config.JwtConfig;
import chungbuk.cityfarmerplus.auth.config.JwtProperties;
import chungbuk.cityfarmerplus.auth.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String SECRET =
            "test-secret-key-with-at-least-thirty-two-bytes";

    @Test
    void issuedTokenContainsUserIdRoleIssuerAndExpiration() {
        JwtProperties properties = new JwtProperties(
                SECRET,
                "https://cityfarmerplus.test",
                Duration.ofHours(1)
        );
        JwtConfig config = new JwtConfig();
        SecretKey secretKey = config.jwtSecretKey(properties);
        JwtEncoder encoder = config.jwtEncoder(secretKey);
        JwtDecoder decoder = config.jwtDecoder(secretKey, properties);
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        JwtTokenProvider provider = new JwtTokenProvider(
                encoder,
                properties,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        JwtTokenProvider.IssuedAccessToken issuedToken =
                provider.issueAccessToken(15L, User.UserType.FARM);
        Jwt decoded = decoder.decode(issuedToken.value());

        assertThat(decoded.getSubject()).isEqualTo("15");
        assertThat(decoded.getClaimAsString("role")).isEqualTo("FARM");
        assertThat(decoded.getIssuer().toString()).isEqualTo("https://cityfarmerplus.test");
        assertThat(decoded.getIssuedAt()).isEqualTo(now);
        assertThat(decoded.getExpiresAt()).isEqualTo(now.plus(Duration.ofHours(1)));
        assertThat(issuedToken.expiresInSeconds()).isEqualTo(3600);
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        JwtProperties properties = new JwtProperties(
                SECRET,
                "https://cityfarmerplus.test",
                Duration.ofHours(1)
        );
        JwtConfig config = new JwtConfig();
        SecretKey signingKey = config.jwtSecretKey(properties);
        JwtEncoder encoder = config.jwtEncoder(signingKey);
        JwtTokenProvider provider = new JwtTokenProvider(encoder, properties);
        String token = provider.issueAccessToken(15L, User.UserType.FARM).value();

        JwtProperties otherProperties = new JwtProperties(
                "different-test-secret-with-at-least-thirty-two-bytes",
                "https://cityfarmerplus.test",
                Duration.ofHours(1)
        );
        SecretKey otherKey = config.jwtSecretKey(otherProperties);
        JwtDecoder otherDecoder = config.jwtDecoder(otherKey, otherProperties);

        assertThatThrownBy(() -> otherDecoder.decode(token))
                .isInstanceOf(JwtException.class);
    }
}
