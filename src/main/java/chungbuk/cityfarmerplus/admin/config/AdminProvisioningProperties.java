package chungbuk.cityfarmerplus.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;

@ConfigurationProperties(prefix = "app.admin-provisioning")
public record AdminProvisioningProperties(
        boolean enabled,
        String key
) {

    private static final int MINIMUM_KEY_BYTES = 32;

    public AdminProvisioningProperties {
        key = key == null ? "" : key;

        if (enabled && key.isBlank()) {
            throw new IllegalArgumentException(
                    "담당자 계정 발급 기능을 활성화하려면 ADMIN_PROVISIONING_KEY가 필요합니다."
            );
        }
        if (enabled && key.getBytes(StandardCharsets.UTF_8).length < MINIMUM_KEY_BYTES) {
            throw new IllegalArgumentException(
                    "ADMIN_PROVISIONING_KEY는 32바이트 이상이어야 합니다."
            );
        }
    }
}
