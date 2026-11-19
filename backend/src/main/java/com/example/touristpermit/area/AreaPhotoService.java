package com.example.touristpermit.area;

import com.example.touristpermit.area.dto.AreaResponse;
import com.example.touristpermit.audit.AuditAction;
import com.example.touristpermit.audit.AuditService;
import com.example.touristpermit.common.exception.ResourceNotFoundException;
import com.example.touristpermit.document.FileStorageService;
import com.example.touristpermit.document.UploadValidator;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.Map;

/**
 * Destination photos managed by administrators. Files are validated like documents
 * (extension, declared type and magic bytes), stored under random names, and the
 * previous file is removed only after the database change commits.
 */
@Service
public class AreaPhotoService {

    public record Photo(Resource resource, String mimeType) {
    }

    private final PermitAreaRepository areas;
    private final UploadValidator validator;
    private final FileStorageService storage;
    private final AuditService audit;
    private final Clock clock;

    public AreaPhotoService(PermitAreaRepository areas, UploadValidator validator, FileStorageService storage,
                            AuditService audit, Clock clock) {
        this.areas = areas;
        this.validator = validator;
        this.storage = storage;
        this.audit = audit;
        this.clock = clock;
    }

    /** Uploads or replaces the area's photo. */
    @Transactional
    public AreaResponse upload(Long areaId, MultipartFile file) {
        PermitArea area = find(areaId);
        UploadValidator.ValidatedUpload upload = validator.validateImage(file);

        String newPath;
        try (InputStream in = file.getInputStream()) {
            newPath = storage.store(in, upload.extension());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String oldPath = area.getPhotoPath();
        afterCompletion(committed -> storage.deleteQuietly(committed ? oldPath : newPath));

        area.setPhoto(newPath, upload.mimeType(), clock.instant());
        audit.record(AuditAction.AREA_UPDATED, "PermitArea", areaId,
                Map.of("photo", oldPath == null ? "added" : "replaced"));
        return AreaResponse.from(area);
    }

    @Transactional
    public AreaResponse remove(Long areaId) {
        PermitArea area = find(areaId);
        if (area.hasPhoto()) {
            String oldPath = area.getPhotoPath();
            area.clearPhoto();
            afterCompletion(committed -> {
                if (committed) {
                    storage.deleteQuietly(oldPath);
                }
            });
            audit.record(AuditAction.AREA_UPDATED, "PermitArea", areaId, Map.of("photo", "removed"));
        }
        return AreaResponse.from(area);
    }

    /** Photo content for public display (destination photos are not sensitive). */
    @Transactional(readOnly = true)
    public Photo load(Long areaId) {
        PermitArea area = find(areaId);
        if (!area.hasPhoto()) {
            throw new ResourceNotFoundException("This area has no photo");
        }
        return new Photo(storage.load(area.getPhotoPath()), area.getPhotoMimeType());
    }

    private PermitArea find(Long id) {
        return areas.findById(id).orElseThrow(() -> new ResourceNotFoundException("Area not found"));
    }

    private interface CompletionAction {
        void run(boolean committed);
    }

    private static void afterCompletion(CompletionAction action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                action.run(status == STATUS_COMMITTED);
            }
        });
    }
}
