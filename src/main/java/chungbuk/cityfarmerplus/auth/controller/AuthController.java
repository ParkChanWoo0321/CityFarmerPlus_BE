package chungbuk.cityfarmerplus.auth.controller;

import chungbuk.cityfarmerplus.auth.dto.AccountWithdrawalRequest;
import chungbuk.cityfarmerplus.auth.dto.LoginIdAvailabilityResponse;
import chungbuk.cityfarmerplus.auth.dto.LoginRequest;
import chungbuk.cityfarmerplus.auth.dto.SignupRequest;
import chungbuk.cityfarmerplus.auth.dto.TokenResponse;
import chungbuk.cityfarmerplus.auth.dto.UserProfileUpdateRequest;
import chungbuk.cityfarmerplus.auth.dto.UserResponse;
import chungbuk.cityfarmerplus.auth.jwt.JwtTokenProvider;
import chungbuk.cityfarmerplus.auth.service.AccountWithdrawalService;
import chungbuk.cityfarmerplus.auth.service.AuthService;
import chungbuk.cityfarmerplus.common.web.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    private final AccountWithdrawalService accountWithdrawalService;

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
        return ResponseEntity.ok(authService.getById(AuthenticatedUser.id(authentication)));
    }

    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateMe(
            Authentication authentication,
            @Valid @RequestBody UserProfileUpdateRequest request
    ) {
        return ResponseEntity.ok(authService.updateProfile(
                AuthenticatedUser.id(authentication),
                request
        ));
    }

    @PostMapping("/withdrawal")
    public ResponseEntity<Void> withdraw(
            Authentication authentication,
            @Valid @RequestBody AccountWithdrawalRequest request
    ) {
        accountWithdrawalService.withdraw(
                AuthenticatedUser.id(authentication),
                request.password()
        );
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }
}
