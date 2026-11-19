package com.example.touristpermit.document;

import com.example.touristpermit.common.exception.ResourceNotFoundException;
import com.example.touristpermit.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Local-filesystem storage rooted at {@code FILE_STORAGE_PATH} (default; {@code STORAGE_TYPE=local}).
 * Stored names are random UUIDs (never derived from user input) and every resolved path is
 * checked to stay inside the root, preventing path traversal. Suitable for a single backend
 * instance; use {@link S3FileStorage} when running several.
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);

    private final Path root;
    private final Clock clock;

    public LocalFileStorage(AppProperties properties, Clock clock) {
        this.root = Path.of(properties.storage().path()).toAbsolutePath().normalize();
        this.clock = clock;
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create file storage directory", e);
        }
    }

    @Override
    public String store(InputStream content, String extension) {
        String relative = FileStorageService.newKey(LocalDate.now(clock), extension);
        Path target = resolve(relative);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store file", e);
        }
        return relative;
    }

    @Override
    public Resource load(String relativePath) {
        Path path = resolve(relativePath);
        if (!Files.isRegularFile(path)) {
            throw new ResourceNotFoundException("Document content not found");
        }
        return new PathResource(path);
    }

    @Override
    public void deleteQuietly(String relativePath) {
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException | RuntimeException e) {
            log.warn("Could not delete stored file", e);
        }
    }

    private Path resolve(String relativePath) {
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new SecurityException("Resolved path escapes storage root");
        }
        return resolved;
    }
}
