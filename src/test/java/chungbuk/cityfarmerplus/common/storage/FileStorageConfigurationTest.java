package chungbuk.cityfarmerplus.common.storage;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;

class FileStorageConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            FileStorageConfig.class,
                            LocalFileStorage.class,
                            S3FileStorage.class
                    );

    @Test
    void usesLocalStorageByDefault() {
        contextRunner
                .withPropertyValues(
                        "app.file-storage.root=build/test-file-storage"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(FileStorage.class);
                    assertThat(context).hasSingleBean(LocalFileStorage.class);
                    assertThat(context).doesNotHaveBean(S3FileStorage.class);
                    assertThat(context).doesNotHaveBean(S3Client.class);
                });
    }

    @Test
    void usesS3StorageOnlyWhenExplicitlySelected() {
        contextRunner
                .withPropertyValues(
                        "app.file-storage.type=s3",
                        "app.file-storage.s3.endpoint=https://example.r2.cloudflarestorage.com",
                        "app.file-storage.s3.region=auto",
                        "app.file-storage.s3.bucket=documents",
                        "app.file-storage.s3.access-key-id=access-key",
                        "app.file-storage.s3.secret-access-key=secret-key",
                        "app.file-storage.s3.path-style=true",
                        "app.file-storage.s3.prefix=cityfarmerplus/prod"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(FileStorage.class);
                    assertThat(context).hasSingleBean(S3FileStorage.class);
                    assertThat(context).doesNotHaveBean(LocalFileStorage.class);
                    assertThat(context).hasSingleBean(S3Client.class);

                    S3Client client = context.getBean(S3Client.class);
                    assertThat(client.serviceClientConfiguration().requestChecksumCalculation())
                            .isEqualTo(RequestChecksumCalculation.WHEN_REQUIRED);
                    assertThat(client.serviceClientConfiguration().responseChecksumValidation())
                            .isEqualTo(ResponseChecksumValidation.WHEN_REQUIRED);
                });
    }

    @Test
    void failsFastWhenRequiredS3SettingsAreMissing() {
        contextRunner
                .withPropertyValues(
                        "app.file-storage.type=s3",
                        "app.file-storage.s3.endpoint=https://example.r2.cloudflarestorage.com",
                        "app.file-storage.s3.region=auto",
                        "app.file-storage.s3.bucket=documents",
                        "app.file-storage.s3.access-key-id=access-key"
                )
                .run(context -> assertThat(context).hasFailed());
    }
}
