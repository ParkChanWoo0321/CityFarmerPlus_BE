package chungbuk.cityfarmerplus.auth.dto;

import lombok.Getter;

@Getter
public class TokenResponse {

    private final String accessToken;
    private final UserResponse user;

    public TokenResponse(String accessToken, UserResponse user) {
        this.accessToken = accessToken;
        this.user = user;
    }
}
