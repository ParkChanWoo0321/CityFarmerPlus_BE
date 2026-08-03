package chungbuk.cityfarmerplus.admin.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AdminProvisioningProperties.class)
public class AdminProvisioningConfig {
}
