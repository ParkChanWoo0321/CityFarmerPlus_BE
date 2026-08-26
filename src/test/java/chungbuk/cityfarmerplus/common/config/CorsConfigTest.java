package chungbuk.cityfarmerplus.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    @Test
    void configuredFrontendOriginCanUseBearerJsonAndMultipartRequests() {
        CorsConfig config = new CorsConfig();
        CorsConfigurationSource source = config.corsConfigurationSource(
                new CorsProperties(List.of("http://localhost:5173"))
        );
        MockHttpServletRequest request = new MockHttpServletRequest(
                "OPTIONS",
                "/api/auth/login"
        );

        CorsConfiguration cors = source.getCorsConfiguration(request);

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOrigins()).containsExactly("http://localhost:5173");
        assertThat(cors.getAllowedMethods())
                .contains("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(cors.getAllowedHeaders())
                .contains(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE);
        assertThat(cors.getExposedHeaders()).contains(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(cors.getAllowCredentials()).isFalse();

        MockHttpServletRequest healthRequest = new MockHttpServletRequest(
                "OPTIONS",
                "/health"
        );
        assertThat(source.getCorsConfiguration(healthRequest)).isSameAs(cors);
    }
}
