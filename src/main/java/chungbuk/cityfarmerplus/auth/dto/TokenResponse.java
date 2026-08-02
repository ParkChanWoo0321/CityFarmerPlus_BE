package chungbuk.cityfarmerplus.auth.dto;

public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UserResponse user
) {

    public static TokenResponse bearer(
            String accessToken,
            long expiresInSeconds,
            UserResponse user
    ) {
        return new TokenResponse(accessToken, "Bearer", expiresInSeconds, user);
    }
}
