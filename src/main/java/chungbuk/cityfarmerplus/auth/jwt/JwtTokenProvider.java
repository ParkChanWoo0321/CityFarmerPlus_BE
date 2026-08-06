package chungbuk.cityfarmerplus.auth.jwt;

import chungbuk.cityfarmerplus.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long expirationMillis;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMillis
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationMillis;
    }

    // 로그인 성공 시 호출: "이 사용자다"라는 정보를 담아 서명된 토큰 문자열을 만들어낸다.
    public String generateToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMillis);

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))          // 토큰의 주인이 누구인지 (userId)
                .claim("userType", user.getUserType().name())    // 커스텀 정보 추가 (도시농부/농가/중개센터)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();                                       // 최종적으로 "eyJhbGci..." 형태의 문자열로 변환
    }

    // 요청이 들어올 때마다 호출: 토큰이 위조되지 않았는지, 만료되지 않았는지 확인한다.
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token); // 서명이 다르거나 만료됐으면 여기서 예외가 발생함
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Long getUserId(String token) {
        Claims claims = parseClaims(token);
        return Long.valueOf(claims.getSubject());
    }

    public String getUserType(String token) {
        Claims claims = parseClaims(token);
        return claims.get("userType", String.class);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
