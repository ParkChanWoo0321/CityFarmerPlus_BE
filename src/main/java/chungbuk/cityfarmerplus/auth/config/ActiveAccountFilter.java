package chungbuk.cityfarmerplus.auth.config;

import chungbuk.cityfarmerplus.auth.service.ActiveAccountVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ActiveAccountFilter extends OncePerRequestFilter {

    private static final String ROLE_CLAIM = "role";

    private final ObjectProvider<ActiveAccountVerifier> verifierProvider;

    public ActiveAccountFilter(ObjectProvider<ActiveAccountVerifier> verifierProvider) {
        this.verifierProvider = verifierProvider;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        ActiveAccountVerifier verifier = verifierProvider.getIfAvailable();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
                || verifier == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Long userId;
        try {
            userId = Long.parseLong(jwtAuthentication.getToken().getSubject());
        } catch (NumberFormatException exception) {
            userId = null;
        }

        String tokenRole = jwtAuthentication.getToken().getClaimAsString(ROLE_CLAIM);
        if (userId == null || !verifier.isValid(userId, tokenRole)) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(
                    "{\"code\":\"INVALID_ACCOUNT\","
                            + "\"message\":\"현재 계정으로 인증할 수 없습니다.\"}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }
}
