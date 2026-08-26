package chungbuk.cityfarmerplus.common.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(
        name = "app.file-storage.type",
        havingValue = "s3"
)
public class S3FileStorage implements FileStorage {

    private static final Pattern SAFE_SEGMENT = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._-]{0,254}"
    );
    private static final Pattern SAFE_EXTENSION = Pattern.compile(
            "[a-z0-9]{1,10}"
    );
    private static final int MAX_STORAGE_KEY_LENGTH = 500;

    private final S3Client s3Client;
    private final String bucket;
    private final String prefix;

    public S3FileStorage(
            S3Client s3Client,
            FileStorageProperties properties
    ) {
        if (properties.type() != FileStorageProperties.Type.S3
                || properties.s3() == null) {
            throw new IllegalArgumentException(
                    "S3 파일 저장소 설정이 필요합니다."
            );
        }
        this.s3Client = s3Client;
        this.bucket = properties.s3().bucket();
        this.prefix = validatePath(
                properties.s3().prefix(),
                true,
                "S3_PREFIX가 올바르지 않습니다."
        );
    }

    @Override
    public StoredFile store(
            String directory,
            MultipartFile file,
            String extension,
            long maxBytes
    ) {
        String safeDirectory = validatePath(
                directory,
                false,
                "파일 저장 경로가 올바르지 않습니다."
        );
        if (file == null) {
            throw new FileStorageException("저장할 파일이 필요합니다.");
        }
        if (extension == null || !SAFE_EXTENSION.matcher(extension).matches()) {
            throw new FileStorageException("파일 확장자가 올바르지 않습니다.");
        }
        if (maxBytes < 0) {
            throw new FileStorageException("파일 크기 제한이 올바르지 않습니다.");
        }
        String storageKey = safeDirectory
                + "/"
                + UUID.randomUUID()
                + "."
                + extension;
        if (storageKey.length() > MAX_STORAGE_KEY_LENGTH) {
            throw new FileStorageException("파일 저장 경로가 너무 깁니다.");
        }

        Path temporaryFile = null;
        try {
            temporaryFile = Files.createTempFile(
                    "cityfarmerplus-s3-upload-",
                    ".tmp"
            );
            StreamedFile streamedFile = streamToTemporaryFile(
                    file,
                    temporaryFile,
                    maxBytes
            );
            PutObjectRequest.Builder request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey(storageKey))
                    .contentLength(streamedFile.sizeBytes())
                    .metadata(Map.of("sha256", streamedFile.sha256()));
            if (file.getContentType() != null
                    && !file.getContentType().isBlank()) {
                request.contentType(file.getContentType());
            }
            s3Client.putObject(
                    request.build(),
                    RequestBody.fromFile(temporaryFile)
            );
            return new StoredFile(
                    storageKey,
                    streamedFile.sizeBytes(),
                    streamedFile.sha256()
            );
        } catch (SdkException exception) {
            throw new FileStorageException(
                    "파일을 원격 저장소에 저장하지 못했습니다.",
                    exception
            );
        } catch (IOException exception) {
            throw new FileStorageException(
                    "파일을 원격 저장소에 저장하지 못했습니다.",
                    exception
            );
        } finally {
            deleteTemporaryFile(temporaryFile);
        }
    }

    @Override
    public void delete(String storageKey) {
        String safeStorageKey = validateStorageKey(storageKey);
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey(safeStorageKey))
                    .build());
        } catch (SdkException exception) {
            throw new FileStorageException(
                    "원격 저장소의 파일을 삭제하지 못했습니다.",
                    exception
            );
        }
    }

    @Override
    public Resource load(String storageKey) {
        String safeStorageKey = validateStorageKey(storageKey);
        String objectKey = objectKey(safeStorageKey);
        try {
            HeadObjectResponse response = s3Client.headObject(
                    HeadObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey)
                            .build()
            );
            Long contentLength = response.contentLength();
            return new S3ObjectResource(
                    s3Client,
                    bucket,
                    objectKey,
                    safeStorageKey,
                    contentLength == null ? -1L : contentLength
            );
        } catch (SdkException exception) {
            throw new FileStorageException(
                    "원격 저장소의 파일을 읽지 못했습니다.",
                    exception
            );
        }
    }

    private StreamedFile streamToTemporaryFile(
            MultipartFile file,
            Path temporaryFile,
            long maxBytes
    ) throws IOException {
        MessageDigest digest = sha256Digest();
        long sizeBytes = 0;
        try (InputStream inputStream = file.getInputStream();
             OutputStream outputStream = Files.newOutputStream(
                     temporaryFile,
                     StandardOpenOption.TRUNCATE_EXISTING,
                     StandardOpenOption.WRITE
             )) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                if (read > maxBytes - sizeBytes) {
                    throw new FileStorageException(
                            "파일이 저장 허용 크기를 초과했습니다."
                    );
                }
                outputStream.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                sizeBytes += read;
            }
        }
        return new StreamedFile(
                sizeBytes,
                HexFormat.of().formatHex(digest.digest())
        );
    }

    private String validateStorageKey(String storageKey) {
        return validatePath(
                storageKey,
                false,
                "파일 저장 키가 올바르지 않습니다."
        );
    }

    private String validatePath(
            String value,
            boolean allowEmpty,
            String message
    ) {
        if (value == null) {
            if (allowEmpty) {
                return "";
            }
            throw new FileStorageException(message);
        }
        if (value.isEmpty() && allowEmpty) {
            return "";
        }
        if (value.isBlank()
                || value.length() > MAX_STORAGE_KEY_LENGTH
                || value.startsWith("/")
                || value.endsWith("/")
                || value.indexOf('\\') >= 0) {
            throw new FileStorageException(message);
        }
        for (String segment : value.split("/", -1)) {
            if (!SAFE_SEGMENT.matcher(segment).matches()) {
                throw new FileStorageException(message);
            }
        }
        return value;
    }

    private String objectKey(String storageKey) {
        if (prefix.isEmpty()) {
            return storageKey;
        }
        return prefix + "/" + storageKey;
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 해시를 사용할 수 없습니다.",
                    exception
            );
        }
    }

    private void deleteTemporaryFile(Path temporaryFile) {
        if (temporaryFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporaryFile);
        } catch (IOException ignored) {
            // 요청의 원래 성공 또는 실패 결과를 보존하기 위해 임시 파일 정리는 최선의 노력으로 끝낸다.
        }
    }

    private record StreamedFile(long sizeBytes, String sha256) {
    }

    private static final class S3ObjectResource extends AbstractResource {

        private final S3Client s3Client;
        private final String bucket;
        private final String objectKey;
        private final String storageKey;
        private final long contentLength;

        private S3ObjectResource(
                S3Client s3Client,
                String bucket,
                String objectKey,
                String storageKey,
                long contentLength
        ) {
            this.s3Client = s3Client;
            this.bucket = bucket;
            this.objectKey = objectKey;
            this.storageKey = storageKey;
            this.contentLength = contentLength;
        }

        @Override
        public InputStream getInputStream() {
            try {
                return s3Client.getObject(GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .build());
            } catch (SdkException exception) {
                throw new FileStorageException(
                        "원격 저장소의 파일 스트림을 열지 못했습니다.",
                        exception
                );
            }
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public boolean isReadable() {
            return true;
        }

        @Override
        public long contentLength() throws IOException {
            if (contentLength >= 0) {
                return contentLength;
            }
            return super.contentLength();
        }

        @Override
        public String getFilename() {
            int separator = storageKey.lastIndexOf('/');
            return separator < 0
                    ? storageKey
                    : storageKey.substring(separator + 1);
        }

        @Override
        public String getDescription() {
            return "S3 object [" + storageKey + "]";
        }
    }
}
