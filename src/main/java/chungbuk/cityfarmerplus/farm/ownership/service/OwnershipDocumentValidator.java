package chungbuk.cityfarmerplus.farm.ownership.service;

import chungbuk.cityfarmerplus.farm.ownership.exception.FarmOwnershipException;
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
public class OwnershipDocumentValidator {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf",
            "jpg",
            "jpeg",
            "png"
    );
    static final int MAX_DOCUMENT_COUNT = 5;
    static final long MAX_DOCUMENT_SIZE = 10L * 1024 * 1024;
    static final long MAX_TOTAL_SIZE = 30L * 1024 * 1024;

    public List<ValidatedDocument> validate(List<MultipartFile> documents) {
        if (documents == null || documents.isEmpty()) {
            throw FarmOwnershipException.documentsRequired();
        }
        if (documents.size() > MAX_DOCUMENT_COUNT) {
            throw FarmOwnershipException.tooManyDocuments();
        }

        long declaredTotalSize = 0;
        for (MultipartFile document : documents) {
            if (document == null || document.isEmpty()) {
                throw FarmOwnershipException.documentsRequired();
            }
            if (document.getSize() > MAX_DOCUMENT_SIZE) {
                throw FarmOwnershipException.documentTooLarge();
            }
            if (document.getSize() > MAX_TOTAL_SIZE - declaredTotalSize) {
                throw FarmOwnershipException.totalSizeTooLarge();
            }
            declaredTotalSize += document.getSize();
        }
        if (declaredTotalSize > MAX_TOTAL_SIZE) {
            throw FarmOwnershipException.totalSizeTooLarge();
        }

        List<ValidatedDocument> validatedDocuments = new ArrayList<>();
        long actualTotalSize = 0;
        for (MultipartFile document : documents) {
            ValidatedDocument validatedDocument = validateOne(document);
            if (validatedDocument.size() > MAX_TOTAL_SIZE - actualTotalSize) {
                throw FarmOwnershipException.totalSizeTooLarge();
            }
            actualTotalSize += validatedDocument.size();
            validatedDocuments.add(validatedDocument);
        }
        return List.copyOf(validatedDocuments);
    }

    private ValidatedDocument validateOne(MultipartFile document) {
        String originalFilename = sanitizeOriginalFilename(document.getOriginalFilename());
        String requestedExtension = extractExtension(originalFilename);

        try (InputStream inputStream = document.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] header = inputStream.readNBytes(8);
            digest.update(header);

            DetectedFileType detectedType = DetectedFileType.detect(header);
            detectedType.verifyExtension(requestedExtension);
            detectedType.verifyDeclaredContentType(document.getContentType());

            long actualSize = header.length;
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                actualSize += read;
                if (actualSize > MAX_DOCUMENT_SIZE) {
                    throw FarmOwnershipException.documentTooLarge();
                }
                digest.update(buffer, 0, read);
            }

            return new ValidatedDocument(
                    document,
                    originalFilename,
                    detectedType.storageExtension,
                    detectedType.contentType,
                    actualSize,
                    HexFormat.of().formatHex(digest.digest())
            );
        } catch (FarmOwnershipException exception) {
            throw exception;
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw FarmOwnershipException.invalidDocumentContent();
        }
    }

    private String sanitizeOriginalFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw FarmOwnershipException.invalidFilename();
        }

        String normalized = originalFilename.replace('\\', '/');
        String filename = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (filename.isBlank()
                || filename.getBytes(StandardCharsets.UTF_8).length > 255
                || filename.chars().anyMatch(character -> character < 32)) {
            throw FarmOwnershipException.invalidFilename();
        }
        return filename;
    }

    private String extractExtension(String filename) {
        int extensionSeparator = filename.lastIndexOf('.');
        if (extensionSeparator <= 0 || extensionSeparator == filename.length() - 1) {
            throw FarmOwnershipException.unsupportedDocumentType();
        }
        String extension = filename.substring(extensionSeparator + 1)
                .toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw FarmOwnershipException.unsupportedDocumentType();
        }
        return extension;
    }

    public record ValidatedDocument(
            MultipartFile source,
            String originalFilename,
            String storageExtension,
            String contentType,
            long size,
            String sha256
    ) {
    }

    private enum DetectedFileType {
        PDF(
                "pdf",
                "application/pdf",
                Set.of("pdf"),
                Set.of("application/pdf", "application/x-pdf")
        ),
        JPEG(
                "jpg",
                "image/jpeg",
                Set.of("jpg", "jpeg"),
                Set.of("image/jpeg", "image/jpg", "image/pjpeg")
        ),
        PNG(
                "png",
                "image/png",
                Set.of("png"),
                Set.of("image/png")
        );

        private final String storageExtension;
        private final String contentType;
        private final Set<String> allowedExtensions;
        private final Set<String> allowedContentTypes;

        DetectedFileType(
                String storageExtension,
                String contentType,
                Set<String> allowedExtensions,
                Set<String> allowedContentTypes
        ) {
            this.storageExtension = storageExtension;
            this.contentType = contentType;
            this.allowedExtensions = allowedExtensions;
            this.allowedContentTypes = allowedContentTypes;
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
            throw FarmOwnershipException.invalidDocumentContent();
        }

        void verifyExtension(String extension) {
            if (!allowedExtensions.contains(extension)) {
                throw FarmOwnershipException.invalidDocumentContent();
            }
        }

        void verifyDeclaredContentType(String declaredContentType) {
            if (declaredContentType == null
                    || declaredContentType.isBlank()
                    || declaredContentType.equalsIgnoreCase("application/octet-stream")) {
                return;
            }
            String normalizedContentType = declaredContentType.toLowerCase(Locale.ROOT);
            if (!allowedContentTypes.contains(normalizedContentType)) {
                throw FarmOwnershipException.invalidDocumentContent();
            }
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
