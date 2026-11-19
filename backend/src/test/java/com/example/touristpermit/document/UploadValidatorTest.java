package com.example.touristpermit.document;

import com.example.touristpermit.common.exception.ApiException;
import com.example.touristpermit.support.TestProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadValidatorTest {

    static final byte[] PDF = "%PDF-1.4\n%test\n".getBytes();
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    static final byte[] EXE = {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0};

    final UploadValidator validator = new UploadValidator(TestProperties.withMaxUpload(DataSize.ofKilobytes(1)));

    @Test
    void acceptsValidPdf() {
        var result = validator.validate(new MockMultipartFile("file", "passport.pdf", "application/pdf", PDF));
        assertThat(result.extension()).isEqualTo("pdf");
        assertThat(result.mimeType()).isEqualTo("application/pdf");
        assertThat(result.displayName()).isEqualTo("passport.pdf");
    }

    @Test
    void rejectsExecutableRenamedToPdf() {
        assertRejected(new MockMultipartFile("file", "virus.pdf", "application/pdf", EXE), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void rejectsPngContentWithPdfExtension() {
        assertRejected(new MockMultipartFile("file", "x.pdf", "application/pdf", PNG), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void rejectsDisallowedExtension() {
        assertRejected(new MockMultipartFile("file", "run.exe", "application/octet-stream", EXE),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertRejected(new MockMultipartFile("file", "page.html", "text/html", "<html>".getBytes()),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void rejectsMismatchedDeclaredContentType() {
        assertRejected(new MockMultipartFile("file", "x.png", "application/pdf", PNG), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void rejectsEmptyAndOversizedFiles() {
        assertRejected(new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[0]),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        byte[] big = new byte[2048];
        System.arraycopy(PDF, 0, big, 0, PDF.length);
        assertRejected(new MockMultipartFile("file", "x.pdf", "application/pdf", big), HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test
    void sanitizesDisplayNameAndStripsPaths() {
        assertThat(UploadValidator.sanitizeDisplayName("../../etc/passwd.pdf")).isEqualTo("passwd.pdf");
        assertThat(UploadValidator.sanitizeDisplayName("C:\\Users\\x\\scan<script>.png")).isEqualTo("scan_script_.png");
        assertThat(UploadValidator.sanitizeDisplayName("..")).isEqualTo("document");
        assertThat(UploadValidator.extensionOf("archive.tar.PDF")).isEqualTo("pdf");
        assertThat(UploadValidator.extensionOf(".pdf")).isNull();
        assertThat(UploadValidator.extensionOf("noext")).isNull();
    }

    private void assertRejected(MockMultipartFile file, HttpStatus status) {
        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(status);
    }
}
