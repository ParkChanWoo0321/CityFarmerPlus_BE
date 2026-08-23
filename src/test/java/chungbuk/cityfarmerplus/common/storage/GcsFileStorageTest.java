package chungbuk.cityfarmerplus.common.storage;

import com.google.cloud.ReadChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GcsFileStorageTest {

    @Mock
    Storage gcs;

    private GcsFileStorage storage;

    @BeforeEach
    void setUp() {
        storage = new GcsFileStorage(
                gcs,
                properties("cityfarmerplus/prod")
        );
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
        when(gcs.createFrom(
                any(BlobInfo.class),
                any(Path.class),
                any(Storage.BlobWriteOption[].class)
        ))
                .thenAnswer(invocation -> {
                    Path path = invocation.getArgument(1);
                    uploaded.set(Files.readAllBytes(path));
                    return org.mockito.Mockito.mock(Blob.class);
                });

        FileStorage.StoredFile result = storage.store(
                "education-certification/10/batch-id",
                file,
                "pdf",
                content.length
        );

        ArgumentCaptor<BlobInfo> blobInfoCaptor =
                ArgumentCaptor.forClass(BlobInfo.class);
        ArgumentCaptor<Storage.BlobWriteOption[]> optionsCaptor =
                ArgumentCaptor.forClass(Storage.BlobWriteOption[].class);
        verify(gcs).createFrom(
                blobInfoCaptor.capture(),
                any(Path.class),
                optionsCaptor.capture()
        );
        BlobInfo blobInfo = blobInfoCaptor.getValue();
        assertThat(result.storageKey())
                .startsWith("education-certification/10/batch-id/")
                .endsWith(".pdf")
                .doesNotContain("cityfarmerplus/prod");
        assertThat(result.sizeBytes()).isEqualTo(content.length);
        assertThat(result.sha256()).isEqualTo(sha256(content));
        assertThat(blobInfo.getBucket()).isEqualTo("documents");
        assertThat(blobInfo.getName())
                .isEqualTo("cityfarmerplus/prod/" + result.storageKey());
        assertThat(blobInfo.getContentType()).isEqualTo("application/pdf");
        assertThat(blobInfo.getMetadata())
                .containsEntry("sha256", sha256(content));
        assertThat(optionsCaptor.getValue()).containsExactly(
                Storage.BlobWriteOption.doesNotExist()
        );
        assertThat(uploaded.get()).isEqualTo(content);
    }

    @Test
    void checksActualStreamSizeBeforeCallingGcs() throws Exception {
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

        verify(gcs, never()).createFrom(
                any(BlobInfo.class),
                any(Path.class),
                any(Storage.BlobWriteOption[].class)
        );
    }

    @Test
    void rejectsUnsafePathsBeforeCallingGcs() {
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

        verifyNoInteractions(gcs);
    }

    @Test
    void loadsAReadableStreamingResource() throws Exception {
        byte[] content = "proof".getBytes();
        Blob blob = org.mockito.Mockito.mock(Blob.class);
        ReadChannel readChannel = readChannel(content);
        when(blob.getSize()).thenReturn((long) content.length);
        when(gcs.get(any(BlobId.class))).thenReturn(blob);
        when(gcs.reader(any(BlobId.class))).thenReturn(readChannel);

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
        ArgumentCaptor<BlobId> getCaptor = ArgumentCaptor.forClass(BlobId.class);
        ArgumentCaptor<BlobId> readerCaptor = ArgumentCaptor.forClass(BlobId.class);
        verify(gcs).get(getCaptor.capture());
        verify(gcs).reader(readerCaptor.capture());
        assertThat(getCaptor.getValue()).isEqualTo(readerCaptor.getValue());
        assertThat(getCaptor.getValue().getName()).isEqualTo(
                "cityfarmerplus/prod/education-certification/10/"
                        + "batch-id/document.pdf"
        );
    }

    @Test
    void deleteRemainsIdempotentForTheSameLogicalKey() {
        BlobId blobId = BlobId.of(
                "documents",
                "cityfarmerplus/prod/education-certification/10/"
                        + "batch-id/document.pdf"
        );
        when(gcs.delete(blobId)).thenReturn(true, false);

        storage.delete("education-certification/10/batch-id/document.pdf");
        storage.delete("education-certification/10/batch-id/document.pdf");

        verify(gcs, times(2)).delete(blobId);
    }

    @Test
    void wrapsGcsFailures() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "documents",
                "certificate.pdf",
                "application/pdf",
                "content".getBytes()
        );
        StorageException failure = new StorageException(500, "failure");
        when(gcs.createFrom(
                any(BlobInfo.class),
                any(Path.class),
                any(Storage.BlobWriteOption[].class)
        ))
                .thenThrow(failure);

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
    void wrapsFailureWhenDeleting() {
        StorageException failure = new StorageException(500, "failure");
        when(gcs.delete(any(BlobId.class))).thenThrow(failure);

        assertThatThrownBy(() -> storage.delete(
                "education-certification/10/batch-id/document.pdf"
        ))
                .isInstanceOf(FileStorageException.class)
                .hasCause(failure);
    }

    @Test
    void wrapsFailureWhenLoadingMetadata() {
        StorageException failure = new StorageException(500, "failure");
        when(gcs.get(any(BlobId.class))).thenThrow(failure);

        assertThatThrownBy(() -> storage.load(
                "education-certification/10/batch-id/document.pdf"
        ))
                .isInstanceOf(FileStorageException.class)
                .hasCause(failure);
    }

    @Test
    void wrapsFailureWhenOpeningResourceStream() {
        Blob blob = org.mockito.Mockito.mock(Blob.class);
        when(blob.getSize()).thenReturn(10L);
        when(gcs.get(any(BlobId.class))).thenReturn(blob);
        StorageException failure = new StorageException(500, "failure");
        when(gcs.reader(any(BlobId.class))).thenThrow(failure);

        Resource resource = storage.load(
                "education-certification/10/batch-id/document.pdf"
        );

        assertThatThrownBy(resource::getInputStream)
                .isInstanceOf(FileStorageException.class)
                .hasCause(failure);
    }

    private ReadChannel readChannel(byte[] content) throws Exception {
        ReadChannel channel = org.mockito.Mockito.mock(ReadChannel.class);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(content);
        when(channel.read(any(ByteBuffer.class))).thenAnswer(invocation -> {
            ByteBuffer buffer = invocation.getArgument(0);
            byte[] bytes = new byte[buffer.remaining()];
            int read = inputStream.read(bytes);
            if (read < 0) {
                return -1;
            }
            buffer.put(bytes, 0, read);
            return read;
        });
        return channel;
    }

    private FileStorageProperties properties(String prefix) {
        return new FileStorageProperties(
                FileStorageProperties.Type.GCS,
                null,
                null,
                new FileStorageProperties.Gcs(
                        "test-project",
                        "documents",
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
