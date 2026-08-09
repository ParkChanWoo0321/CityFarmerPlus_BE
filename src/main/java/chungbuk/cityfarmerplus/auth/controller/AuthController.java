package chungbuk.cityfarmerplus.auth.controller;

import chungbuk.cityfarmerplus.auth.dto.LoginIdAvailabilityResponse;
import chungbuk.cityfarmerplus.auth.dto.LoginRequest;
import chungbuk.cityfarmerplus.auth.dto.SignupRequest;
import chungbuk.cityfarmerplus.auth.dto.TokenResponse;
import chungbuk.cityfarmerplus.auth.dto.UserResponse;
import chungbuk.cityfarmerplus.auth.exception.AuthException;
import chungbuk.cityfarmerplus.auth.jwt.JwtTokenProvider;
import chungbuk.cityfarmerplus.auth.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signup(
            @Valid @RequestBody SignupRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(authService.signup(request));
    }

    @GetMapping("/check-id")
    public ResponseEntity<LoginIdAvailabilityResponse> checkLoginIdAvailability(
            @RequestParam
            @Pattern(
                    regexp = "^[a-z0-9_]{4,30}$",
                    message = "아이디는 4~30자의 영문 소문자, 숫자, 밑줄만 사용할 수 있습니다."
            )
            String loginId
    ) {
        return ResponseEntity.ok(authService.checkLoginIdAvailability(loginId));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        AuthService.LoginResult loginResult = authService.login(request);
        JwtTokenProvider.IssuedAccessToken token = jwtTokenProvider.issueAccessToken(
                loginResult.userId(),
                loginResult.userType()
        );

        return ResponseEntity.ok(TokenResponse.bearer(
                token.value(),
                token.expiresInSeconds(),
                loginResult.user()
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        try {
            Long userId = Long.valueOf(authentication.getName());
            return ResponseEntity.ok(authService.getById(userId));
        } catch (NumberFormatException exception) {
            throw AuthException.invalidAuthentication();
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }
}
