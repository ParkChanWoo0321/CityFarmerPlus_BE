package chungbuk.cityfarmerplus.proxy.entity;

import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyRegistrationLogSchemaContractTest {

    @Test
    void actionTypeColumnFitsEveryPersistedEnumName() throws Exception {
        Column column = ProxyRegistrationLog.class
                .getDeclaredField("actionType")
                .getAnnotation(Column.class);
        int longestEnumName = Arrays.stream(ProxyRegistrationLog.ActionType.values())
                .map(Enum::name)
                .mapToInt(String::length)
                .max()
                .orElseThrow();

        assertThat(column.length()).isGreaterThanOrEqualTo(longestEnumName);
    }
}
