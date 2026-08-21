package chungbuk.cityfarmerplus.common.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(FileStorageProperties.class)
public class FileStorageConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(
            name = "app.file-storage.type",
            havingValue = "s3"
    )
    public S3Client fileStorageS3Client(FileStorageProperties properties) {
        FileStorageProperties.S3 s3 = properties.s3();
        return S3Client.builder()
                .endpointOverride(endpoint(s3.endpoint()))
                .region(Region.of(s3.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                s3.accessKeyId(),
                                s3.secretAccessKey()
                        )
                ))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(s3.pathStyle())
                        .chunkedEncodingEnabled(false)
                        .build())
                .build();
    }

    private URI endpoint(String value) {
        try {
            URI endpoint = URI.create(value);
            if (!("http".equalsIgnoreCase(endpoint.getScheme())
                    || "https".equalsIgnoreCase(endpoint.getScheme()))
                    || endpoint.getHost() == null) {
                throw new IllegalArgumentException();
            }
            return endpoint;
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException(
                    "S3_ENDPOINT는 유효한 HTTP 또는 HTTPS URL이어야 합니다."
            );
        }
    }
}
