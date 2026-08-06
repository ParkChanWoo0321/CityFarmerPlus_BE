package chungbuk.cityfarmerplus.auth.config;

import chungbuk.cityfarmerplus.auth.jwt.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(auth -> auth
                // 회원가입/로그인/중복확인은 토큰이 없는 상태에서도 호출 가능해야 하므로 permitAll
                .requestMatchers("/api/auth/signup", "/api/auth/login", "/api/auth/check-id").permitAll()
                .requestMatchers("/error").permitAll()
                // 도시농부 전용 API는 userType이 URBAN_FARMER인 토큰만 접근 가능
                .requestMatchers("/api/urban-farmer/**").hasRole("URBAN_FARMER")
                // 그 외(예: /api/auth/me, /api/auth/logout)는 유효한 토큰이 있어야 접근 가능
                .anyRequest().authenticated()
            )

            // 인증 실패 시 기본 동작(로그인 폼으로 리다이렉트) 대신, REST API답게 401만 응답하도록 지정
            .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) ->
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "인증이 필요합니다.")
            ))

            // 우리가 만든 JwtAuthenticationFilter를, Spring Security의 기본 로그인 필터보다 먼저 실행되게 등록
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
