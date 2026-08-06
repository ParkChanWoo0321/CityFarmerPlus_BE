package chungbuk.cityfarmerplus.auth.controller;

import chungbuk.cityfarmerplus.auth.dto.LoginRequest;
import chungbuk.cityfarmerplus.auth.dto.SignupRequest;
import chungbuk.cityfarmerplus.auth.dto.TokenResponse;
import chungbuk.cityfarmerplus.auth.dto.UserResponse;
import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.auth.jwt.JwtTokenProvider;
import chungbuk.cityfarmerplus.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest request) {
        User user = authService.signup(request);
        return ResponseEntity.ok(new UserResponse(user));
    }

    @GetMapping("/check-id")
    public ResponseEntity<Boolean> checkLoginIdAvailable(@RequestParam String loginId) {
        return ResponseEntity.ok(authService.isLoginIdAvailable(loginId));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = authService.login(request);
        String token = jwtTokenProvider.generateToken(user);

        return ResponseEntity.ok(new TokenResponse(token, new UserResponse(user)));
    }

    // 클라이언트가 로그인 상태인지 확인할 때 호출.
    // 토큰이 없거나 유효하지 않으면, 이 메서드에 도달하기도 전에 SecurityConfig 설정에 의해 401이 응답된다.
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        User user = authService.getById(userId);
        return ResponseEntity.ok(new UserResponse(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout() {
        return ResponseEntity.ok("서버에는 별도로 저장된 로그인 정보가 없습니다. 클라이언트에서 보관 중인 토큰을 삭제해주세요.");
    }
}
