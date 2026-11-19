package com.example.touristpermit.document;

import com.example.touristpermit.common.exception.ApiException;
import com.example.touristpermit.config.AppProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates uploads by size, extension allow-list, declared MIME type AND the file's
 * actual leading bytes (magic numbers). The original filename is never trusted.
 */
@Component
public class UploadValidator {

    public record ValidatedUpload(String extension, String mimeType, String displayName) {
    }

    private static final Map<String, String> EXTENSION_TO_MIME = Map.of(
            "pdf", "application/pdf",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp");

    /** Destination photos: images only, independent of the document allow-list. */
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> IMAGE_MIME_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] RIFF_MAGIC = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP_MAGIC = {'W', 'E', 'B', 'P'};

    private final Set<String> allowedExtensions;
    private final Set<String> allowedMimeTypes;
    private final long maxBytes;

    public UploadValidator(AppProperties properties) {
        AppProperties.Storage s = properties.storage();
        this.allowedExtensions = lower(s.allowedExtensions());
        this.allowedMimeTypes = lower(s.allowedMimeTypes());
        this.maxBytes = s.maxFileSize().toBytes();
    }

    /** Validates a supporting document against the configured allow-lists. */
    public ValidatedUpload validate(MultipartFile file) {
        return validate(file, allowedExtensions, allowedMimeTypes);
    }

    /** Validates an image upload (JPEG, PNG or WebP). */
    public ValidatedUpload validateImage(MultipartFile file) {
        return validate(file, IMAGE_EXTENSIONS, IMAGE_MIME_TYPES);
    }

    private ValidatedUpload validate(MultipartFile file, Set<String> allowedExtensions, Set<String> allowedMimeTypes) {
        if (file == null || file.isEmpty()) {
            throw invalid("File is empty");
        }
        if (file.getSize() > maxBytes) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded file exceeds the maximum allowed size");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = extensionOf(original);
        if (extension == null || !allowedExtensions.contains(extension) || !EXTENSION_TO_MIME.containsKey(extension)) {
            throw invalid("File type not allowed. Allowed: " + String.join(", ", allowedExtensions));
        }
        String expectedMime = EXTENSION_TO_MIME.get(extension);
        if (!allowedMimeTypes.contains(expectedMime)) {
            throw invalid("File type not allowed");
        }
        String declared = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!declared.equals(expectedMime)) {
            throw invalid("Declared content type does not match file extension");
        }
        String detected = sniff(file);
        if (!expectedMime.equals(detected)) {
            throw invalid("File content does not match its declared type");
        }
        return new ValidatedUpload(extension, expectedMime, sanitizeDisplayName(original));
    }

    static String extensionOf(String filename) {
        String base = stripPath(filename);
        int dot = base.lastIndexOf('.');
        if (dot <= 0 || dot == base.length() - 1) {
            return null;
        }
        return base.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Keeps a safe, display-only version of the client file name. */
    static String sanitizeDisplayName(String filename) {
        String cleaned = stripPath(filename).replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        if (cleaned.isEmpty() || cleaned.chars().allMatch(c -> c == '.')) {
            cleaned = "document";
        }
        return cleaned.length() > 200 ? cleaned.substring(cleaned.length() - 200) : cleaned;
    }

    private static String stripPath(String filename) {
        String name = filename.replace('\\', '/');
        return name.substring(name.lastIndexOf('/') + 1);
    }

    private static String sniff(MultipartFile file) {
        byte[] head = new byte[12];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (read >= 12 && startsWith(head, read, RIFF_MAGIC)
                && Arrays.equals(head, 8, 12, WEBP_MAGIC, 0, WEBP_MAGIC.length)) {
            return "image/webp";
        }
        if (startsWith(head, read, PDF_MAGIC)) {
            return "application/pdf";
        }
        if (startsWith(head, read, PNG_MAGIC)) {
            return "image/png";
        }
        if (startsWith(head, read, JPEG_MAGIC)) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }

    private static boolean startsWith(byte[] data, int length, byte[] prefix) {
        return length >= prefix.length && Arrays.equals(data, 0, prefix.length, prefix, 0, prefix.length);
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, message);
    }

    private static Set<String> lower(java.util.List<String> values) {
        return values.stream().map(v -> v.trim().toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }
}
