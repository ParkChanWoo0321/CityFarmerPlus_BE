package chungbuk.cityfarmerplus.common.storage;

import org.springframework.stereotype.Component;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(FileStorageProperties properties) {
        this.root = Path.of(properties.root()).toAbsolutePath().normalize();
    }

    @Override
    public StoredFile store(
            String directory,
            MultipartFile file,
            String extension,
            long maxBytes
    ) {
        Path targetDirectory = resolveWithinRoot(directory);
        if (extension == null || !extension.matches("[a-z0-9]{1,10}")) {
            throw new FileStorageException("파일 확장자가 올바르지 않습니다.");
        }
        if (maxBytes < 0) {
            throw new FileStorageException("파일 크기 제한이 올바르지 않습니다.");
        }
        String storedFilename = UUID.randomUUID() + "." + extension;
        MessageDigest digest = sha256Digest();
        Path temporaryFile = null;

        try {
            Files.createDirectories(root);
            Path realRoot = root.toRealPath();
            Path realDirectory = createDirectoryWithinRoot(targetDirectory, realRoot);

            Path target = realDirectory.resolve(storedFilename);
            temporaryFile = Files.createTempFile(realDirectory, ".upload-", ".tmp");
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
            moveAtomically(temporaryFile, target);
            String storageKey = realRoot.relativize(target)
                    .toString()
                    .replace('\\', '/');
            return new StoredFile(
                    storageKey,
                    sizeBytes,
                    HexFormat.of().formatHex(digest.digest())
            );
        } catch (IOException exception) {
            deleteQuietly(temporaryFile);
            deleteEmptyParentsQuietly(targetDirectory);
            throw new FileStorageException("파일을 저장하지 못했습니다.", exception);
        } catch (RuntimeException exception) {
            deleteQuietly(temporaryFile);
            deleteEmptyParentsQuietly(targetDirectory);
            throw exception;
        }
    }

    @Override
    public void delete(String storageKey) {
        Path target = resolveWithinRoot(storageKey);
        try {
            if (!Files.exists(target.getParent())) {
                return;
            }
            Path realRoot = root.toRealPath();
            Path realParent = target.getParent().toRealPath();
            if (!realParent.startsWith(realRoot)) {
                throw new FileStorageException("허용되지 않은 파일 저장 경로입니다.");
            }
            Path realTarget = realParent.resolve(target.getFileName());
            if (Files.exists(realTarget, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isRegularFile(realTarget, LinkOption.NOFOLLOW_LINKS)) {
                throw new FileStorageException("삭제할 저장 파일이 올바르지 않습니다.");
            }
            Files.deleteIfExists(realTarget);
            deleteEmptyParents(realParent, realRoot);
        } catch (IOException exception) {
            throw new FileStorageException("파일을 삭제하지 못했습니다.", exception);
        }
    }

    @Override
    public Resource load(String storageKey) {
        Path target = resolveWithinRoot(storageKey);
        try {
            if (!Files.exists(root) || !Files.exists(target.getParent())) {
                throw new FileStorageException("저장된 파일을 찾을 수 없습니다.");
            }
            Path realRoot = root.toRealPath();
            Path realParent = target.getParent().toRealPath();
            if (!realParent.startsWith(realRoot)) {
                throw new FileStorageException("허용되지 않은 파일 저장 경로입니다.");
            }
            Path realTarget = realParent.resolve(target.getFileName());
            if (!Files.isRegularFile(realTarget, LinkOption.NOFOLLOW_LINKS)) {
                throw new FileStorageException("저장된 파일을 찾을 수 없습니다.");
            }
            return new FileSystemResource(realTarget);
        } catch (IOException exception) {
            throw new FileStorageException("파일을 읽지 못했습니다.", exception);
        }
    }

    private Path resolveWithinRoot(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new FileStorageException("파일 저장 경로가 올바르지 않습니다.");
        }

        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new FileStorageException("허용되지 않은 파일 저장 경로입니다.");
        }
        return resolved;
    }

    private void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 해시를 사용할 수 없습니다.", exception);
        }
    }

    private Path createDirectoryWithinRoot(
            Path targetDirectory,
            Path realRoot
    ) throws IOException {
        Path current = realRoot;
        for (Path segment : root.relativize(targetDirectory)) {
            current = current.resolve(segment);
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    Files.createDirectory(current);
                } catch (FileAlreadyExistsException ignored) {
                    // 다른 요청이 같은 상위 폴더를 먼저 만들 수 있으므로 아래에서 다시 검증한다.
                }
            }
            Path realCurrent = current.toRealPath();
            if (!realCurrent.startsWith(realRoot)) {
                throw new FileStorageException("허용되지 않은 파일 저장 경로입니다.");
            }
            if (!Files.isDirectory(realCurrent)) {
                throw new FileStorageException("파일 저장 폴더가 올바르지 않습니다.");
            }
            current = realCurrent;
        }
        return current;
    }

    private void deleteQuietly(Path target) {
        if (target == null) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // 저장 실패 시 생성 중이던 파일을 최선의 노력으로 정리한다.
        }
    }

    private void deleteEmptyParents(Path directory, Path realRoot) {
        Path current = directory;
        while (current.startsWith(realRoot) && !current.equals(realRoot)) {
            try {
                Files.deleteIfExists(current);
                current = current.getParent();
            } catch (DirectoryNotEmptyException exception) {
                return;
            } catch (IOException exception) {
                // 파일 삭제 자체는 성공했으므로 빈 상위 폴더 정리는 최선의 노력으로 끝낸다.
                return;
            }
        }
    }

    private void deleteEmptyParentsQuietly(Path directory) {
        try {
            if (!Files.exists(root)
                    || !Files.exists(directory)
                    || !Files.isDirectory(directory)) {
                return;
            }
            Path realRoot = root.toRealPath();
            Path realDirectory = directory.toRealPath();
            if (realDirectory.startsWith(realRoot)) {
                deleteEmptyParents(realDirectory, realRoot);
            }
        } catch (IOException ignored) {
            // 원래 저장 실패를 유지하기 위해 빈 폴더 정리는 최선의 노력으로 끝낸다.
        }
    }
}
