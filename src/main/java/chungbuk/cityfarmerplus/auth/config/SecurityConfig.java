package chungbuk.cityfarmerplus.auth.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import chungbuk.cityfarmerplus.auth.service.ActiveAccountVerifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String ROLE_CLAIM = "role";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ActiveAccountFilter activeAccountFilter
    ) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/check-id")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/internal/center-admins")
                        .permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/integrations/education/progress-events"
                        )
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/education/courses")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/market-prices/**")
                        .permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/job-postings",
                                "/api/job-postings/{postingId}",
                                "/api/support/faqs"
                        )
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/health", "/health/live")
                        .permitAll()
                        .requestMatchers("/error")
                        .permitAll()
                        .requestMatchers("/api/urban-farmers/**")
                        .hasRole("URBAN_FARMER")
                        .requestMatchers("/api/admin/**")
                        .hasRole("CENTER_ADMIN")
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(this::convertAuthentication))
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(
                                        response,
                                        HttpServletResponse.SC_UNAUTHORIZED,
                                        "UNAUTHORIZED",
                                        "인증이 필요합니다."
                                )))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, cause) ->
                                writeError(
                                        response,
                                        HttpServletResponse.SC_UNAUTHORIZED,
                                        "UNAUTHORIZED",
                                        "인증이 필요합니다."
                                ))
                        .accessDeniedHandler((request, response, cause) ->
                                writeError(
                                        response,
                                        HttpServletResponse.SC_FORBIDDEN,
                                        "ACCESS_DENIED",
                                        "접근 권한이 없습니다."
                                )))
                .addFilterAfter(
                        activeAccountFilter,
                        BearerTokenAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public ActiveAccountFilter activeAccountFilter(
            ObjectProvider<ActiveAccountVerifier> verifierProvider
    ) {
        return new ActiveAccountFilter(verifierProvider);
    }

    private JwtAuthenticationToken convertAuthentication(Jwt jwt) {
        String role = jwt.getClaimAsString(ROLE_CLAIM);
        List<GrantedAuthority> authorities = role == null || role.isBlank()
                ? List.of()
                : List.of(new SimpleGrantedAuthority("ROLE_" + role));
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    private void writeError(
            HttpServletResponse response,
            int status,
            String code,
            String message
    ) throws IOException {
        response.setStatus(status);
        response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                "{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}"
        );
    }
}
