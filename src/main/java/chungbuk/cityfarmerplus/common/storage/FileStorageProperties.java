package chungbuk.cityfarmerplus.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "app.file-storage")
public record FileStorageProperties(
        Type type,
        String root,
        S3 s3,
        Gcs gcs
) {

    public FileStorageProperties(String root) {
        this(Type.LOCAL, root, null, null);
    }

    public FileStorageProperties(Type type, String root, S3 s3) {
        this(type, root, s3, null);
    }

    @ConstructorBinding
    public FileStorageProperties {
        type = type == null ? Type.LOCAL : type;
        if (type == Type.LOCAL && isBlank(root)) {
            throw new IllegalArgumentException(
                    "local 파일 저장소에는 FILE_STORAGE_ROOT가 필요합니다."
            );
        }
        if (type == Type.S3) {
            if (s3 == null) {
                throw new IllegalArgumentException(
                        "s3 파일 저장소 설정이 필요합니다."
                );
            }
            s3.validate();
        }
        if (type == Type.GCS) {
            if (gcs == null) {
                throw new IllegalArgumentException(
                        "gcs 파일 저장소 설정이 필요합니다."
                );
            }
            gcs.validate();
        }
    }

    public enum Type {
        LOCAL,
        S3,
        GCS
    }

    public record Gcs(
            String projectId,
            String bucket,
            String prefix
    ) {

        public Gcs {
            projectId = normalize(projectId);
            bucket = normalize(bucket);
            prefix = normalize(prefix);
        }

        private void validate() {
            if (isBlank(bucket)) {
                throw new IllegalArgumentException(
                        "GCS_BUCKET가 필요합니다."
                );
            }
        }

        private static String normalize(String value) {
            return value == null ? "" : value.trim();
        }
    }

    public record S3(
            String endpoint,
            String region,
            String bucket,
            String accessKeyId,
            String secretAccessKey,
            boolean pathStyle,
            String prefix
    ) {

        public S3 {
            prefix = prefix == null ? "" : prefix.trim();
        }

        private void validate() {
            require(endpoint, "S3_ENDPOINT");
            require(region, "S3_REGION");
            require(bucket, "S3_BUCKET");
            require(accessKeyId, "S3_ACCESS_KEY_ID");
            require(secretAccessKey, "S3_SECRET_ACCESS_KEY");
        }

        private void require(String value, String environmentName) {
            if (isBlank(value)) {
                throw new IllegalArgumentException(
                        environmentName + "가 필요합니다."
                );
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
