package chungbuk.cityfarmerplus.health;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/health/live")
    public Map<String, String> liveness() {
        return Map.of("status", "UP");
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (result != null && result == 1) {
                return ResponseEntity.ok(Map.of("status", "UP", "database", "UP"));
            }
        } catch (RuntimeException ignored) {
            // Health responses intentionally do not expose database error details.
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", "DOWN", "database", "DOWN"));
    }
}
