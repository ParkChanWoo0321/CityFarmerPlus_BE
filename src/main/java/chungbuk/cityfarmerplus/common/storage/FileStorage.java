package chungbuk.cityfarmerplus.common.storage;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;

public interface FileStorage {

    StoredFile store(
            String directory,
            MultipartFile file,
            String extension,
            long maxBytes
    );

    void delete(String storageKey);

    Resource load(String storageKey);

    record StoredFile(String storageKey, long sizeBytes, String sha256) {
    }
}
