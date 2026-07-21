package chungbuk.cityfarmerplus.auth.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String token = resolveToken(request);

        if (token != null && jwtTokenProvider.validateToken(token)) {
            Long userId = jwtTokenProvider.getUserId(token);
            String userType = jwtTokenProvider.getUserType(token);

            // "ROLE_URBAN_FARMER", "ROLE_FARM" 처럼 권한 이름을 만든다.
            // 나중에 권한 처리를 만들 때 .hasAuthority("ROLE_FARM") 형태로 이걸 그대로 활용할 수 있다.
            List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + userType));

            // principal 자리에 userId를 넣어두면, 컨트롤러에서 Authentication만 받아도 "누구인지" 바로 꺼낼 수 있다.
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);

            // 이 한 줄이 세션의 session.setAttribute(...)와 같은 역할을 한다.
            // 다만 세션과 달리, 이건 "이 요청 하나에 한해서만" 유효하다. 요청이 끝나면 사라진다.
            // (서버가 아무것도 기억하지 않는다는 JWT의 핵심 특징이 바로 여기서 드러난다.)
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response); // 다음 필터/컨트롤러로 요청을 계속 진행시킴
    }

    // "Authorization: Bearer eyJhbGci..." 헤더에서 토큰 문자열만 추출
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
