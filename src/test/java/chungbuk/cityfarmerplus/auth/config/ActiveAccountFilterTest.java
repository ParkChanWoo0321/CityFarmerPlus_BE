package chungbuk.cityfarmerplus.auth.config;

import chungbuk.cityfarmerplus.auth.service.ActiveAccountVerifier;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActiveAccountFilterTest {

    @Mock
    private ObjectProvider<ActiveAccountVerifier> verifierProvider;

    @Mock
    private ActiveAccountVerifier verifier;

    @Mock
    private FilterChain filterChain;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activeJwtAccountContinuesFilterChain() throws Exception {
        when(verifierProvider.getIfAvailable()).thenReturn(verifier);
        when(verifier.isValid(15L, "URBAN_FARMER")).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(authentication(
                "15",
                "URBAN_FARMER"
        ));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ActiveAccountFilter(verifierProvider)
                .doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void invalidCurrentAccountIsRejected() throws Exception {
        when(verifierProvider.getIfAvailable()).thenReturn(verifier);
        when(verifier.isValid(15L, "URBAN_FARMER")).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(authentication(
                "15",
                "URBAN_FARMER"
        ));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ActiveAccountFilter(verifierProvider)
                .doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString())
                .contains("INVALID_ACCOUNT");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void nonNumericJwtSubjectIsRejectedWithoutRepositoryVerification() throws Exception {
        when(verifierProvider.getIfAvailable()).thenReturn(verifier);
        SecurityContextHolder.getContext().setAuthentication(authentication(
                "not-a-number",
                "URBAN_FARMER"
        ));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ActiveAccountFilter(verifierProvider)
                .doFilter(request, response, filterChain);

        verify(verifier, never()).isValid(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());
        verify(filterChain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString())
                .contains("INVALID_ACCOUNT");
    }

    @Test
    void publicRequestWithoutTokenContinuesFilterChain() throws Exception {
        when(verifierProvider.getIfAvailable()).thenReturn(verifier);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/auth/login"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ActiveAccountFilter(verifierProvider)
                .doFilter(request, response, filterChain);

        verify(verifier, never()).isValid(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any()
        );
        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private JwtAuthenticationToken authentication(String subject, String role) {
        Instant issuedAt = Instant.parse("2026-08-09T00:00:00Z");
        Jwt jwt = Jwt.withTokenValue("jwt")
                .header("alg", "HS256")
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(3600))
                .claim("role", role)
                .build();
        return new JwtAuthenticationToken(jwt);
    }
}
