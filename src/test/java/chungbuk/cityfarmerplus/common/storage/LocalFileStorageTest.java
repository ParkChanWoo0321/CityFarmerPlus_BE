package chungbuk.cityfarmerplus.common.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void storesWithGeneratedNameInsideConfiguredRootAndDeletesIdempotently()
            throws Exception {
        LocalFileStorage storage = storage();
        byte[] content = "document-content".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "documents",
                "토지대장.pdf",
                "application/pdf",
                content
        );

        FileStorage.StoredFile stored = storage.store(
                "farm-ownership/10/batch-id",
                file,
                "pdf",
                content.length
        );
        String storageKey = stored.storageKey();

        assertThat(storageKey)
                .startsWith("farm-ownership/10/batch-id/")
                .endsWith(".pdf")
                .doesNotContain("토지대장");
        Path storedFile = temporaryDirectory.resolve(storageKey);
        assertThat(Files.readAllBytes(storedFile)).isEqualTo(content);
        assertThat(stored.sizeBytes()).isEqualTo(content.length);
        assertThat(stored.sha256()).isEqualTo(sha256(content));

        storage.delete(storageKey);
        storage.delete(storageKey);

        assertThat(storedFile).doesNotExist();
        assertThat(temporaryDirectory.resolve("farm-ownership/10/batch-id"))
                .doesNotExist();
    }

    @Test
    void sameOriginalFilenameNeverOverwritesAnExistingFile() {
        LocalFileStorage storage = storage();
        MockMultipartFile file = new MockMultipartFile(
                "documents",
                "토지대장.pdf",
                "application/pdf",
                "content".getBytes()
        );

        String firstKey = storage.store(
                "farm-ownership/10/batch-id",
                file,
                "pdf",
                file.getSize()
        ).storageKey();
        String secondKey = storage.store(
                "farm-ownership/10/batch-id",
                file,
                "pdf",
                file.getSize()
        ).storageKey();

        assertThat(firstKey).isNotEqualTo(secondKey);
        assertThat(temporaryDirectory.resolve(firstKey)).exists();
        assertThat(temporaryDirectory.resolve(secondKey)).exists();
    }

    @Test
    void rejectsPathsOutsideConfiguredRoot() {
        LocalFileStorage storage = storage();
        MockMultipartFile file = new MockMultipartFile(
                "documents",
                "land.pdf",
                "application/pdf",
                "content".getBytes()
        );

        assertThatThrownBy(() -> storage.store(
                "../outside",
                file,
                "pdf",
                file.getSize()
        ))
                .isInstanceOf(FileStorageException.class);
        assertThatThrownBy(() -> storage.delete("../outside/file.pdf"))
                .isInstanceOf(FileStorageException.class);
    }

    @Test
    void rejectsUntrustedExtensions() {
        LocalFileStorage storage = storage();
        MockMultipartFile file = new MockMultipartFile(
                "documents",
                "land.pdf",
                "application/pdf",
                "content".getBytes()
        );

        assertThatThrownBy(() -> storage.store(
                "farm-ownership/10/batch-id",
                file,
                "../../exe",
                file.getSize()
        )).isInstanceOf(FileStorageException.class);
    }

    @Test
    void removesTemporaryFileAndEmptyDirectoriesWhenStreamingFails()
            throws Exception {
        LocalFileStorage storage = storage();
        MultipartFile brokenFile = org.mockito.Mockito.mock(MultipartFile.class);
        org.mockito.Mockito.when(brokenFile.getInputStream())
                .thenThrow(new IOException("read failed"));

        assertThatThrownBy(() -> storage.store(
                "farm-ownership/10/batch-id",
                brokenFile,
                "pdf",
                1024L
        )).isInstanceOf(FileStorageException.class);

        try (java.util.stream.Stream<Path> files = Files.walk(temporaryDirectory)) {
            assertThat(files.map(path -> path.getFileName().toString()))
                    .noneMatch(filename -> filename.startsWith(".upload-"));
        }
        assertThat(temporaryDirectory.resolve("farm-ownership/10/batch-id"))
                .doesNotExist();
    }

    @Test
    void neverDeletesAnExistingFileMistakenForAStorageDirectory()
            throws Exception {
        LocalFileStorage storage = storage();
        Path parent = temporaryDirectory.resolve("farm-ownership/10");
        Files.createDirectories(parent);
        Path existingFile = parent.resolve("batch-id");
        Files.writeString(existingFile, "keep-me");
        MockMultipartFile upload = new MockMultipartFile(
                "documents",
                "land.pdf",
                "application/pdf",
                "content".getBytes()
        );

        assertThatThrownBy(() -> storage.store(
                "farm-ownership/10/batch-id",
                upload,
                "pdf",
                upload.getSize()
        )).isInstanceOf(FileStorageException.class);

        assertThat(existingFile).exists();
        assertThat(Files.readString(existingFile)).isEqualTo("keep-me");
    }

    @Test
    void deleteRejectsDirectoryKeys() throws Exception {
        LocalFileStorage storage = storage();
        Path directory = temporaryDirectory.resolve("farm-ownership/10/batch-id");
        Files.createDirectories(directory);

        assertThatThrownBy(() -> storage.delete(
                "farm-ownership/10/batch-id"
        )).isInstanceOf(FileStorageException.class);

        assertThat(directory).exists();
    }

    @Test
    void concurrentStoresCanCreateTheSameParentDirectories() throws Exception {
        LocalFileStorage storage = storage();
        MockMultipartFile file = new MockMultipartFile(
                "documents",
                "land.pdf",
                "application/pdf",
                "content".getBytes()
        );
        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<Callable<FileStorage.StoredFile>> tasks = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            int batch = index;
            tasks.add(() -> storage.store(
                    "farm-ownership/10/batch-" + batch,
                    file,
                    "pdf",
                    file.getSize()
            ));
        }

        try {
            List<Future<FileStorage.StoredFile>> futures = executor.invokeAll(tasks);
            List<String> storageKeys = new ArrayList<>();
            for (Future<FileStorage.StoredFile> future : futures) {
                storageKeys.add(future.get().storageKey());
            }

            assertThat(storageKeys).hasSize(20).doesNotHaveDuplicates();
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void stopsWritingAsSoonAsTheStorageLimitIsExceeded() {
        LocalFileStorage storage = storage();
        MockMultipartFile file = new MockMultipartFile(
                "documents",
                "land.pdf",
                "application/pdf",
                "too-large".getBytes()
        );

        assertThatThrownBy(() -> storage.store(
                "farm-ownership/10/batch-id",
                file,
                "pdf",
                3L
        )).isInstanceOf(FileStorageException.class);

        assertThat(temporaryDirectory.resolve("farm-ownership/10/batch-id"))
                .doesNotExist();
    }

    private LocalFileStorage storage() {
        return new LocalFileStorage(
                new FileStorageProperties(temporaryDirectory.toString())
        );
    }

    private String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(content)
        );
    }
}
