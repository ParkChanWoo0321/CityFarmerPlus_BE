package chungbuk.cityfarmerplus.common.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3FileStorageTest {

    @Mock
    S3Client s3Client;

    private S3FileStorage storage;

    @BeforeEach
    void setUp() {
        storage = new S3FileStorage(s3Client, properties("cityfarmerplus/prod"));
    }

    @Test
    void streamsUploadWithSizeLimitAndSha256Metadata() throws Exception {
        byte[] content = "document-content".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "documents",
                "certificate.pdf",
                "application/pdf",
                content
        );
        AtomicReference<byte[]> uploaded = new AtomicReference<>();
        when(s3Client.putObject(
                any(PutObjectRequest.class),
                any(RequestBody.class)
        )).thenAnswer(invocation -> {
            RequestBody body = invocation.getArgument(1);
            try (var inputStream = body.contentStreamProvider().newStream()) {
                uploaded.set(inputStream.readAllBytes());
            }
            return PutObjectResponse.builder().build();
        });

        FileStorage.StoredFile result = storage.store(
                "education-certification/10/batch-id",
                file,
                "pdf",
                content.length
        );

        ArgumentCaptor<PutObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(
                requestCaptor.capture(),
                any(RequestBody.class)
        );
        PutObjectRequest request = requestCaptor.getValue();
        assertThat(result.storageKey())
                .startsWith("education-certification/10/batch-id/")
                .endsWith(".pdf")
                .doesNotContain("cityfarmerplus/prod");
        assertThat(result.sizeBytes()).isEqualTo(content.length);
        assertThat(result.sha256()).isEqualTo(sha256(content));
        assertThat(request.bucket()).isEqualTo("documents");
        assertThat(request.key())
                .isEqualTo("cityfarmerplus/prod/" + result.storageKey());
        assertThat(request.contentLength()).isEqualTo(content.length);
        assertThat(request.contentType()).isEqualTo("application/pdf");
        assertThat(request.metadata())
                .containsEntry("sha256", sha256(content));
        assertThat(uploaded.get()).isEqualTo(content);
    }

    @Test
    void checksActualStreamSizeEvenWhenDeclaredSizeIsSmaller() throws Exception {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.getInputStream()).thenReturn(
                new ByteArrayInputStream("too-large".getBytes())
        );

        assertThatThrownBy(() -> storage.store(
                "education-certification/10/batch-id",
                file,
                "pdf",
                3L
        )).isInstanceOf(FileStorageException.class);

        verify(s3Client, never()).putObject(
                any(PutObjectRequest.class),
                any(RequestBody.class)
        );
    }

    @Test
    void rejectsUnsafePathsBeforeCallingS3() {
        MockMultipartFile file = new MockMultipartFile(
                "documents",
                "certificate.pdf",
                "application/pdf",
                "content".getBytes()
        );

        assertThatThrownBy(() -> storage.store(
                "../outside",
                file,
                "pdf",
                file.getSize()
        )).isInstanceOf(FileStorageException.class);
        assertThatThrownBy(() -> storage.delete("../outside/file.pdf"))
                .isInstanceOf(FileStorageException.class);
        assertThatThrownBy(() -> storage.load("/outside/file.pdf"))
                .isInstanceOf(FileStorageException.class);

        verifyNoInteractions(s3Client);
    }

    @Test
    void loadsAReadableStreamingResource() throws Exception {
        byte[] content = "proof".getBytes();
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder()
                        .contentLength((long) content.length)
                        .build());
        ResponseInputStream<GetObjectResponse> responseInputStream =
                new ResponseInputStream<>(
                        GetObjectResponse.builder()
                                .contentLength((long) content.length)
                                .build(),
                        AbortableInputStream.create(
                                new ByteArrayInputStream(content)
                        )
                );
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenReturn(responseInputStream);

        Resource resource = storage.load(
                "education-certification/10/batch-id/document.pdf"
        );

        assertThat(resource.exists()).isTrue();
        assertThat(resource.isReadable()).isTrue();
        assertThat(resource.contentLength()).isEqualTo(content.length);
        assertThat(resource.getFilename()).isEqualTo("document.pdf");
        try (var inputStream = resource.getInputStream()) {
            assertThat(inputStream.readAllBytes()).isEqualTo(content);
        }
        ArgumentCaptor<HeadObjectRequest> headCaptor =
                ArgumentCaptor.forClass(HeadObjectRequest.class);
        ArgumentCaptor<GetObjectRequest> getCaptor =
                ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).headObject(headCaptor.capture());
        verify(s3Client).getObject(getCaptor.capture());
        assertThat(headCaptor.getValue().key()).isEqualTo(
                "cityfarmerplus/prod/education-certification/10/"
                        + "batch-id/document.pdf"
        );
        assertThat(getCaptor.getValue().key())
                .isEqualTo(headCaptor.getValue().key());
    }

    @Test
    void deleteRemainsIdempotentForTheSameLogicalKey() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());
        String storageKey =
                "education-certification/10/batch-id/document.pdf";

        storage.delete(storageKey);
        storage.delete(storageKey);

        ArgumentCaptor<DeleteObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client, org.mockito.Mockito.times(2))
                .deleteObject(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues())
                .extracting(DeleteObjectRequest::key)
                .containsOnly(
                        "cityfarmerplus/prod/" + storageKey
                );
    }

    @Test
    void wrapsSdkFailuresInFileStorageException() {
        MockMultipartFile file = new MockMultipartFile(
                "documents",
                "certificate.pdf",
                "application/pdf",
                "content".getBytes()
        );
        SdkClientException failure = SdkClientException.create("failure");
        when(s3Client.putObject(
                any(PutObjectRequest.class),
                any(RequestBody.class)
        )).thenThrow(failure);

        assertThatThrownBy(() -> storage.store(
                "education-certification/10/batch-id",
                file,
                "pdf",
                file.getSize()
        ))
                .isInstanceOf(FileStorageException.class)
                .hasCause(failure);
    }

    @Test
    void wrapsSdkFailureWhenDeleting() {
        SdkClientException failure = SdkClientException.create("failure");
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(failure);

        assertThatThrownBy(() -> storage.delete(
                "education-certification/10/batch-id/document.pdf"
        ))
                .isInstanceOf(FileStorageException.class)
                .hasCause(failure);
    }

    @Test
    void wrapsSdkFailureWhenLoadingMetadata() {
        SdkClientException failure = SdkClientException.create("failure");
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(failure);

        assertThatThrownBy(() -> storage.load(
                "education-certification/10/batch-id/document.pdf"
        ))
                .isInstanceOf(FileStorageException.class)
                .hasCause(failure);
    }

    @Test
    void wrapsSdkFailureWhenOpeningResourceStream() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(10L).build());
        SdkClientException failure = SdkClientException.create("failure");
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(failure);

        Resource resource = storage.load(
                "education-certification/10/batch-id/document.pdf"
        );

        assertThatThrownBy(resource::getInputStream)
                .isInstanceOf(FileStorageException.class)
                .hasCause(failure);
    }

    private FileStorageProperties properties(String prefix) {
        return new FileStorageProperties(
                FileStorageProperties.Type.S3,
                null,
                new FileStorageProperties.S3(
                        "https://example.r2.cloudflarestorage.com",
                        "auto",
                        "documents",
                        "access-key",
                        "secret-key",
                        true,
                        prefix
                )
        );
    }

    private String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(content)
        );
    }
}
