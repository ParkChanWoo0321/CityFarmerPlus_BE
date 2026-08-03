package chungbuk.cityfarmerplus.admin.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminProvisioningPropertiesTest {

    @Test
    void disabledProvisioningAllowsAnEmptyKey() {
        AdminProvisioningProperties properties =
                new AdminProvisioningProperties(false, "");

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.key()).isEmpty();
    }

    @Test
    void enabledProvisioningRequiresAKey() {
        assertThatThrownBy(() -> new AdminProvisioningProperties(true, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ADMIN_PROVISIONING_KEY");
    }

    @Test
    void enabledProvisioningRejectsKeysShorterThanThirtyTwoBytes() {
        assertThatThrownBy(() ->
                new AdminProvisioningProperties(true, "change-me-before-use")
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ADMIN_PROVISIONING_KEY는 32바이트 이상이어야 합니다.");
    }

    @Test
    void enabledProvisioningAcceptsAKeyOfAtLeastThirtyTwoBytes() {
        AdminProvisioningProperties properties = new AdminProvisioningProperties(
                true,
                "center-admin-provisioning-key-with-32-bytes"
        );

        assertThat(properties.enabled()).isTrue();
    }
}
