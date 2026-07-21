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

        // ▼▼▼ 세션 때 session.setAttribute(...)가 있던 자리 ▼▼▼
        // 서버 메모리에 아무것도 저장하지 않는다. 대신 "이 사람이 맞다"는 증명서(토큰) 자체를 만들어서 돌려준다.
        String token = jwtTokenProvider.generateToken(user);
        // ▲▲▲ ▲▲▲

        return ResponseEntity.ok(new TokenResponse(token, new UserResponse(user)));
    }

    // 클라이언트가 로그인 상태인지 확인할 때 호출.
    // Authentication은 우리가 만든 JwtAuthenticationFilter가 SecurityContext에 넣어둔 바로 그 객체다.
    // 토큰이 없거나 유효하지 않으면, 이 메서드에 도달하기도 전에 SecurityConfig 설정에 의해 401이 응답된다.
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        User user = authService.getById(userId);
        return ResponseEntity.ok(new UserResponse(user));
    }

    // ⚠️ 세션 때와 의미가 달라지는 부분.
    // 세션은 session.invalidate()로 서버가 "이 사람 로그아웃시켰다"는 걸 실제로 기억할 수 있었지만,
    // JWT는 서버가 토큰에 대해 아무것도 기억하지 않기 때문에, 서버 쪽에서 강제로 토큰을 무효화할 방법이 없다.
    // (토큰은 만료시간이 될 때까지 계속 유효하다.)
    // 그래서 실제 로그아웃 처리는 "클라이언트가 가지고 있는 토�큰을 스스로 삭제하는 것"으로 이루어진다.
    // 이 엔드포인트는 그 사실을 클라이언트에게 안내해주는 역할 정도만 한다.
    @PostMapping("/logout")
    public ResponseEntity<String> logout() {
        return ResponseEntity.ok("서버에는 별도로 저장된 로그인 정보가 없습니다. 클라이언트에서 보관 중인 토큰을 삭제해주세요.");
    }
}
