package com.example.coach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.coach.exception.ApiException;
import com.example.coach.support.TestDocuments;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ResumeParserServiceTest {

    private final ResumeParserService parser = new ResumeParserService();

    @Test
    void extractsTextFromPdf() {
        ResumeParserService.ParsedDocument document = parser.parse("cv.pdf", TestDocuments.pdf(TestDocuments.RESUME));
        assertThat(document.contentType()).isEqualTo(ResumeParserService.PDF);
        assertThat(document.text()).contains("Senior Java Developer", "Kubernetes").doesNotContain("\r", "\n\n\n");
    }

    @Test
    void extractsTextFromDocx() {
        ResumeParserService.ParsedDocument document = parser.parse("cv.docx", TestDocuments.docx(TestDocuments.RESUME));
        assertThat(document.contentType()).isEqualTo(ResumeParserService.DOCX);
        assertThat(document.text()).contains("Mentored 4 junior engineers");
    }

    @Test
    void detectsTypeFromContentNotFileName() {
        byte[] plainText = TestDocuments.RESUME.getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> parser.parse("looks-like.pdf", plainText))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
    }

    @Test
    void rejectsEmptyAndOversizedFiles() {
        assertThatThrownBy(() -> parser.parse("cv.pdf", new byte[0])).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> parser.parse("cv.pdf", new byte[(int) ResumeParserService.MAX_BYTES + 1]))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
    }

    @Test
    void rejectsDocumentsWithoutText() {
        assertThatThrownBy(() -> parser.parse("scan.pdf", TestDocuments.pdf(" ")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("scanned image");
    }
}
