package chungbuk.cityfarmerplus.farm.ownership.service;

import chungbuk.cityfarmerplus.farm.ownership.exception.FarmOwnershipException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OwnershipDocumentValidatorTest {

    private final OwnershipDocumentValidator validator =
            new OwnershipDocumentValidator();

    @Test
    void acceptsPdfJpegAndPngUsingActualFileSignatures() throws Exception {
        byte[] pdf = "%PDF-1.7\ncontent".getBytes();
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01};
        byte[] png = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01
        };

        List<OwnershipDocumentValidator.ValidatedDocument> result = validator.validate(
                List.of(
                        file("land.PDF", "application/pdf", pdf),
                        file("photo.JPEG", "image/jpeg", jpeg),
                        file("map.png", "application/octet-stream", png)
                )
        );

        assertThat(result).hasSize(3);
        assertThat(result.get(0).storageExtension()).isEqualTo("pdf");
        assertThat(result.get(0).contentType()).isEqualTo("application/pdf");
        assertThat(result.get(0).size()).isEqualTo(pdf.length);
        assertThat(result.get(0).sha256()).isEqualTo(sha256(pdf));
        assertThat(result.get(1).storageExtension()).isEqualTo("jpg");
        assertThat(result.get(2).contentType()).isEqualTo("image/png");
    }

    @Test
    void stripsClientPathFromDisplayFilename() {
        byte[] pdf = "%PDF-1.7\ncontent".getBytes();

        OwnershipDocumentValidator.ValidatedDocument result = validator.validate(
                List.of(file("../../documents/토지대장.pdf", "application/pdf", pdf))
        ).get(0);

        assertThat(result.originalFilename()).isEqualTo("토지대장.pdf");
    }

    @Test
    void rejectsMissingEmptyAndExcessiveDocumentLists() {
        assertCode(() -> validator.validate(null), "OWNERSHIP_DOCUMENTS_REQUIRED");
        assertCode(() -> validator.validate(List.of()), "OWNERSHIP_DOCUMENTS_REQUIRED");
        assertCode(
                () -> validator.validate(List.of(
                        pdfFile("1.pdf"),
                        pdfFile("2.pdf"),
                        pdfFile("3.pdf"),
                        pdfFile("4.pdf"),
                        pdfFile("5.pdf"),
                        pdfFile("6.pdf")
                )),
                "TOO_MANY_OWNERSHIP_DOCUMENTS"
        );
        assertCode(
                () -> validator.validate(List.of(new MockMultipartFile(
                        "documents",
                        "empty.pdf",
                        "application/pdf",
                        new byte[0]
                ))),
                "OWNERSHIP_DOCUMENTS_REQUIRED"
        );

        List<MultipartFile> containingNull = new ArrayList<>();
        containingNull.add(null);
        assertCode(
                () -> validator.validate(containingNull),
                "OWNERSHIP_DOCUMENTS_REQUIRED"
        );
    }

    @Test
    void rejectsUnsupportedExtensionsAndMismatchedContent() {
        byte[] pdf = "%PDF-1.7\ncontent".getBytes();
        byte[] png = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        };

        assertCode(
                () -> validator.validate(List.of(file(
                        "land.exe",
                        "application/pdf",
                        pdf
                ))),
                "UNSUPPORTED_OWNERSHIP_DOCUMENT_TYPE"
        );
        assertCode(
                () -> validator.validate(List.of(file(
                        "land.pdf",
                        "application/pdf",
                        png
                ))),
                "INVALID_OWNERSHIP_DOCUMENT_CONTENT"
        );
        assertCode(
                () -> validator.validate(List.of(file(
                        "land.pdf",
                        "image/png",
                        pdf
                ))),
                "INVALID_OWNERSHIP_DOCUMENT_CONTENT"
        );
        assertCode(
                () -> validator.validate(List.of(file(
                        "land.pdf",
                        "application/pdf",
                        "not-a-pdf".getBytes()
                ))),
                "INVALID_OWNERSHIP_DOCUMENT_CONTENT"
        );
    }

    @Test
    void rejectsDeclaredAndActualFilesOverTenMegabytes() throws Exception {
        MultipartFile declaredOversized = mock(MultipartFile.class);
        when(declaredOversized.isEmpty()).thenReturn(false);
        when(declaredOversized.getSize()).thenReturn(
                OwnershipDocumentValidator.MAX_DOCUMENT_SIZE + 1
        );

        assertCode(
                () -> validator.validate(List.of(declaredOversized)),
                "OWNERSHIP_DOCUMENT_TOO_LARGE"
        );

        MultipartFile actualOversized = mock(MultipartFile.class);
        when(actualOversized.isEmpty()).thenReturn(false);
        when(actualOversized.getSize()).thenReturn(1L);
        when(actualOversized.getOriginalFilename()).thenReturn("land.pdf");
        when(actualOversized.getContentType()).thenReturn("application/pdf");
        when(actualOversized.getInputStream()).thenAnswer(invocation ->
                fixedSizePdfStream(OwnershipDocumentValidator.MAX_DOCUMENT_SIZE + 1));

        assertCode(
                () -> validator.validate(List.of(actualOversized)),
                "OWNERSHIP_DOCUMENT_TOO_LARGE"
        );
    }

    @Test
    void rejectsCombinedFilesOverThirtyMegabytes() {
        MultipartFile[] documents = new MultipartFile[4];
        for (int index = 0; index < documents.length; index++) {
            MultipartFile document = mock(MultipartFile.class);
            when(document.isEmpty()).thenReturn(false);
            when(document.getSize()).thenReturn(8L * 1024 * 1024);
            documents[index] = document;
        }

        assertCode(
                () -> validator.validate(List.of(documents)),
                "OWNERSHIP_DOCUMENTS_TOTAL_SIZE_TOO_LARGE"
        );
    }

    @Test
    void rejectsActualCombinedStreamSizeWhenDeclaredSizesAreIncorrect()
            throws Exception {
        List<MultipartFile> documents = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            MultipartFile document = mock(MultipartFile.class);
            when(document.isEmpty()).thenReturn(false);
            when(document.getSize()).thenReturn(1L);
            when(document.getOriginalFilename()).thenReturn("land-" + index + ".pdf");
            when(document.getContentType()).thenReturn("application/pdf");
            when(document.getInputStream()).thenAnswer(invocation ->
                    fixedSizePdfStream(8L * 1024 * 1024));
            documents.add(document);
        }

        assertCode(
                () -> validator.validate(documents),
                "OWNERSHIP_DOCUMENTS_TOTAL_SIZE_TOO_LARGE"
        );
    }

    @Test
    void enforcesFilenameRulesUsingUtf8Bytes() {
        byte[] pdf = "%PDF-1.7\ncontent".getBytes();

        assertCode(
                () -> validator.validate(List.of(file(
                        null,
                        "application/pdf",
                        pdf
                ))),
                "INVALID_OWNERSHIP_DOCUMENT_FILENAME"
        );
        assertCode(
                () -> validator.validate(List.of(file(
                        " ",
                        "application/pdf",
                        pdf
                ))),
                "INVALID_OWNERSHIP_DOCUMENT_FILENAME"
        );
        assertCode(
                () -> validator.validate(List.of(file(
                        "bad\u0001.pdf",
                        "application/pdf",
                        pdf
                ))),
                "INVALID_OWNERSHIP_DOCUMENT_FILENAME"
        );
        assertCode(
                () -> validator.validate(List.of(file(
                        "a".repeat(252) + ".pdf",
                        "application/pdf",
                        pdf
                ))),
                "INVALID_OWNERSHIP_DOCUMENT_FILENAME"
        );

        OwnershipDocumentValidator.ValidatedDocument maximumLength =
                validator.validate(List.of(file(
                        "a".repeat(251) + ".pdf",
                        "application/pdf",
                        pdf
                ))).get(0);
        assertThat(maximumLength.originalFilename().getBytes(StandardCharsets.UTF_8))
                .hasSize(255);
    }

    @Test
    void convertsInputStreamFailureToInvalidDocumentContent() throws Exception {
        MultipartFile document = mock(MultipartFile.class);
        when(document.isEmpty()).thenReturn(false);
        when(document.getSize()).thenReturn(1L);
        when(document.getOriginalFilename()).thenReturn("land.pdf");
        when(document.getContentType()).thenReturn("application/pdf");
        when(document.getInputStream()).thenThrow(new IOException("read failed"));

        assertCode(
                () -> validator.validate(List.of(document)),
                "INVALID_OWNERSHIP_DOCUMENT_CONTENT"
        );
    }

    private MockMultipartFile pdfFile(String filename) {
        return file(filename, "application/pdf", "%PDF-1.7\ncontent".getBytes());
    }

    private MockMultipartFile file(
            String filename,
            String contentType,
            byte[] content
    ) {
        return new MockMultipartFile(
                "documents",
                filename,
                contentType,
                content
        );
    }

    private void assertCode(ThrowingCall call, String expectedCode) {
        assertThatThrownBy(call::run)
                .isInstanceOf(FarmOwnershipException.class)
                .extracting("code")
                .isEqualTo(expectedCode);
    }

    private String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(content)
        );
    }

    private InputStream fixedSizePdfStream(long size) {
        byte[] header = "%PDF-1.7".getBytes();
        return new InputStream() {
            private final ByteArrayInputStream prefix = new ByteArrayInputStream(header);
            private long remaining = size - header.length;

            @Override
            public int read() throws IOException {
                int prefixByte = prefix.read();
                if (prefixByte != -1) {
                    return prefixByte;
                }
                if (remaining == 0) {
                    return -1;
                }
                remaining--;
                return 0;
            }

            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException {
                int prefixRead = prefix.read(buffer, offset, length);
                if (prefixRead > 0) {
                    return prefixRead;
                }
                if (remaining == 0) {
                    return -1;
                }
                int count = (int) Math.min(length, remaining);
                java.util.Arrays.fill(buffer, offset, offset + count, (byte) 0);
                remaining -= count;
                return count;
            }
        };
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run() throws Exception;
    }
}
