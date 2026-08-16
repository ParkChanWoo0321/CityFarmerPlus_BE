package chungbuk.cityfarmerplus.education.service;

import chungbuk.cityfarmerplus.common.exception.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class EducationDocumentValidator {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf",
            "jpg",
            "jpeg",
            "png"
    );
    static final int MAX_DOCUMENT_COUNT = 5;
    static final long MAX_DOCUMENT_SIZE = 10L * 1024 * 1024;
    static final long MAX_TOTAL_SIZE = 30L * 1024 * 1024;

    public List<ValidatedEducationDocument> validate(List<MultipartFile> documents) {
        if (documents == null || documents.isEmpty()) {
            throw error(HttpStatus.BAD_REQUEST, "EDUCATION_DOCUMENTS_REQUIRED", "이수증 파일을 하나 이상 첨부해야 합니다.");
        }
        if (documents.size() > MAX_DOCUMENT_COUNT) {
            throw error(HttpStatus.BAD_REQUEST, "TOO_MANY_EDUCATION_DOCUMENTS", "이수증 파일은 최대 5개까지 첨부할 수 있습니다.");
        }

        long declaredTotalSize = 0;
        for (MultipartFile document : documents) {
            if (document == null || document.isEmpty()) {
                throw error(HttpStatus.BAD_REQUEST, "EDUCATION_DOCUMENTS_REQUIRED", "빈 이수증 파일은 첨부할 수 없습니다.");
            }
            if (document.getSize() > MAX_DOCUMENT_SIZE) {
                throw tooLarge("각 이수증 파일은 10MiB 이하여야 합니다.");
            }
            if (document.getSize() > MAX_TOTAL_SIZE - declaredTotalSize) {
                throw tooLarge("이수증 파일의 전체 크기는 30MiB 이하여야 합니다.");
            }
            declaredTotalSize += document.getSize();
        }

        List<ValidatedEducationDocument> validated = new ArrayList<>();
        long actualTotalSize = 0;
        for (MultipartFile document : documents) {
            ValidatedEducationDocument item = validateOne(document);
            if (item.size() > MAX_TOTAL_SIZE - actualTotalSize) {
                throw tooLarge("이수증 파일의 전체 크기는 30MiB 이하여야 합니다.");
            }
            actualTotalSize += item.size();
            validated.add(item);
        }
        return List.copyOf(validated);
    }

    private ValidatedEducationDocument validateOne(MultipartFile document) {
        String filename = sanitizeFilename(document.getOriginalFilename());
        String extension = extractExtension(filename);
        try (InputStream input = document.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] header = input.readNBytes(8);
            digest.update(header);
            DetectedFileType detected = DetectedFileType.detect(header);
            detected.verifyExtension(extension);
            detected.verifyContentType(document.getContentType());

            long actualSize = header.length;
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (read > MAX_DOCUMENT_SIZE - actualSize) {
                    throw tooLarge("각 이수증 파일은 10MiB 이하여야 합니다.");
                }
                actualSize += read;
                digest.update(buffer, 0, read);
            }
            return new ValidatedEducationDocument(
                    document,
                    filename,
                    detected.storageExtension,
                    detected.contentType,
                    actualSize,
                    HexFormat.of().formatHex(digest.digest())
            );
        } catch (DomainException exception) {
            throw exception;
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw error(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_EDUCATION_DOCUMENT",
                    "이수증 파일 내용을 확인할 수 없습니다."
            );
        }
    }

    private String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_EDUCATION_FILENAME", "이수증 파일명이 올바르지 않습니다.");
        }
        String normalized = originalFilename.replace('\\', '/');
        String filename = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (filename.isBlank()
                || filename.getBytes(StandardCharsets.UTF_8).length > 255
                || filename.chars().anyMatch(character -> character < 32)) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_EDUCATION_FILENAME", "이수증 파일명이 올바르지 않습니다.");
        }
        return filename;
    }

    private String extractExtension(String filename) {
        int separator = filename.lastIndexOf('.');
        if (separator <= 0 || separator == filename.length() - 1) {
            throw unsupportedType();
        }
        String extension = filename.substring(separator + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw unsupportedType();
        }
        return extension;
    }

    private DomainException unsupportedType() {
        return error(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_EDUCATION_DOCUMENT_TYPE",
                "이수증은 PDF, JPG, JPEG, PNG 형식만 첨부할 수 있습니다."
        );
    }

    private DomainException invalidContent() {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_EDUCATION_DOCUMENT",
                "파일 확장자와 실제 이수증 파일 형식이 일치하지 않습니다."
        );
    }

    private DomainException tooLarge(String message) {
        return error(HttpStatus.CONTENT_TOO_LARGE, "EDUCATION_DOCUMENT_TOO_LARGE", message);
    }

    private DomainException error(HttpStatus status, String code, String message) {
        return new DomainException(status, code, message);
    }

    public record ValidatedEducationDocument(
            MultipartFile source,
            String originalFilename,
            String storageExtension,
            String contentType,
            long size,
            String sha256
    ) {
    }

    private enum DetectedFileType {
        PDF("pdf", "application/pdf", Set.of("pdf"), Set.of("application/pdf", "application/x-pdf")),
        JPEG("jpg", "image/jpeg", Set.of("jpg", "jpeg"), Set.of("image/jpeg", "image/jpg", "image/pjpeg")),
        PNG("png", "image/png", Set.of("png"), Set.of("image/png"));

        private final String storageExtension;
        private final String contentType;
        private final Set<String> extensions;
        private final Set<String> contentTypes;

        DetectedFileType(
                String storageExtension,
                String contentType,
                Set<String> extensions,
                Set<String> contentTypes
        ) {
            this.storageExtension = storageExtension;
            this.contentType = contentType;
            this.extensions = extensions;
            this.contentTypes = contentTypes;
        }

        static DetectedFileType detect(byte[] header) {
            if (startsWith(header, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
                return PDF;
            }
            if (startsWith(header, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
                return JPEG;
            }
            if (startsWith(header, new byte[]{
                    (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
            })) {
                return PNG;
            }
            throw new DomainException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_EDUCATION_DOCUMENT",
                    "이수증 파일 내용을 확인할 수 없습니다."
            );
        }

        void verifyExtension(String extension) {
            if (!extensions.contains(extension)) {
                throw invalidTypeContent();
            }
        }

        void verifyContentType(String declaredContentType) {
            if (declaredContentType == null
                    || declaredContentType.isBlank()
                    || declaredContentType.equalsIgnoreCase("application/octet-stream")) {
                return;
            }
            if (!contentTypes.contains(declaredContentType.toLowerCase(Locale.ROOT))) {
                throw invalidTypeContent();
            }
        }

        private static DomainException invalidTypeContent() {
            return new DomainException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_EDUCATION_DOCUMENT",
                    "파일 확장자와 실제 이수증 파일 형식이 일치하지 않습니다."
            );
        }

        private static boolean startsWith(byte[] value, byte[] prefix) {
            if (value.length < prefix.length) {
                return false;
            }
            for (int index = 0; index < prefix.length; index++) {
                if (value[index] != prefix[index]) {
                    return false;
                }
            }
            return true;
        }
    }
}
