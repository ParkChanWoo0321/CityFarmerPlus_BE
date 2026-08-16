package chungbuk.cityfarmerplus.education.service;

import chungbuk.cityfarmerplus.common.exception.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EducationDocumentValidatorTest {

    private final EducationDocumentValidator validator =
            new EducationDocumentValidator();

    @Test
    void acceptsPdfAndUsesDetectedCanonicalMetadata() {
        MockMultipartFile document = new MockMultipartFile(
                "documents",
                "교육 이수증.pdf",
                "application/pdf",
                "%PDF-1.7 test certificate".getBytes(StandardCharsets.US_ASCII)
        );

        var validated = validator.validate(List.of(document));

        assertThat(validated).hasSize(1);
        assertThat(validated.get(0).storageExtension()).isEqualTo("pdf");
        assertThat(validated.get(0).contentType()).isEqualTo("application/pdf");
        assertThat(validated.get(0).sha256()).hasSize(64);
    }

    @Test
    void rejectsSpoofedImageExtension() {
        MockMultipartFile document = new MockMultipartFile(
                "documents",
                "가짜.png",
                "image/png",
                "%PDF-1.7 not png".getBytes(StandardCharsets.US_ASCII)
        );

        assertThatThrownBy(() -> validator.validate(List.of(document)))
                .isInstanceOf(DomainException.class)
                .extracting("code")
                .isEqualTo("INVALID_EDUCATION_DOCUMENT");
    }

    @Test
    void rejectsMoreThanFiveDocuments() {
        MockMultipartFile document = new MockMultipartFile(
                "documents",
                "certificate.pdf",
                "application/pdf",
                "%PDF-1.7".getBytes(StandardCharsets.US_ASCII)
        );

        assertThatThrownBy(() -> validator.validate(List.of(
                document,
                document,
                document,
                document,
                document,
                document
        )))
                .isInstanceOf(DomainException.class)
                .extracting("code")
                .isEqualTo("TOO_MANY_EDUCATION_DOCUMENTS");
    }
}
