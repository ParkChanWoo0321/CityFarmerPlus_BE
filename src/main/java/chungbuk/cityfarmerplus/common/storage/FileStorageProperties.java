package chungbuk.cityfarmerplus.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.file-storage")
public record FileStorageProperties(String root) {

    public FileStorageProperties {
        if (root == null || root.isBlank()) {
            throw new IllegalArgumentException("FILE_STORAGE_ROOT가 필요합니다.");
        }
    }
}
