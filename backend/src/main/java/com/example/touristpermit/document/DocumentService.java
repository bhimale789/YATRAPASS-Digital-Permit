package com.example.touristpermit.document;

import com.example.touristpermit.application.ApplicationStatus;
import com.example.touristpermit.application.PermitApplication;
import com.example.touristpermit.application.PermitApplicationRepository;
import com.example.touristpermit.audit.AuditAction;
import com.example.touristpermit.audit.AuditService;
import com.example.touristpermit.common.exception.BusinessRuleException;
import com.example.touristpermit.common.exception.ConflictException;
import com.example.touristpermit.common.exception.ResourceNotFoundException;
import com.example.touristpermit.config.AppProperties;
import com.example.touristpermit.document.dto.DocumentResponse;
import com.example.touristpermit.security.CurrentUser;
import com.example.touristpermit.tourist.TouristService;
import com.example.touristpermit.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class DocumentService {

    private final PermitDocumentRepository documents;
    private final PermitApplicationRepository applications;
    private final FileStorageService storage;
    private final UploadValidator validator;
    private final TouristService touristService;
    private final UserRepository users;
    private final AuditService audit;
    private final Clock clock;
    private final int maxPerApplication;

    public DocumentService(PermitDocumentRepository documents, PermitApplicationRepository applications,
                           FileStorageService storage, UploadValidator validator, TouristService touristService,
                           UserRepository users, AuditService audit, Clock clock, AppProperties properties) {
        this.documents = documents;
        this.applications = applications;
        this.storage = storage;
        this.validator = validator;
        this.touristService = touristService;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
        this.maxPerApplication = properties.storage().maxDocumentsPerApplication();
    }

    @Transactional
    public DocumentResponse upload(Long applicationId, DocumentType type, MultipartFile file) {
        PermitApplication application = ownApplication(applicationId);
        if (!application.getStatus().isOpen()) {
            throw new ConflictException("Documents can only be added while the application is pending or under review");
        }
        if (documents.countByApplicationId(applicationId) >= maxPerApplication) {
            throw new BusinessRuleException("Maximum of " + maxPerApplication + " documents per application reached");
        }
        UploadValidator.ValidatedUpload upload = validator.validate(file);

        String path;
        try (InputStream in = file.getInputStream()) {
            path = storage.store(in, upload.extension());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // Remove the stored file if the surrounding transaction rolls back.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storage.deleteQuietly(path);
                }
            }
        });

        PermitDocument doc = documents.save(new PermitDocument(application, type, upload.displayName(), path,
                upload.mimeType(), file.getSize(), clock.instant()));
        audit.record(AuditAction.DOCUMENT_UPLOADED, "PermitDocument", doc.getId(),
                Map.of("applicationId", applicationId, "type", type.name()));
        return DocumentResponse.from(doc);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> listForApplication(Long applicationId) {
        return documents.findByApplicationIdOrderByUploadedAtAsc(applicationId).stream()
                .map(DocumentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> listOwn(Long applicationId) {
        ownApplication(applicationId);
        return listForApplication(applicationId);
    }

    @Transactional(readOnly = true)
    public DocumentContent contentForOwner(Long applicationId, Long documentId) {
        PermitApplication application = ownApplication(applicationId);
        PermitDocument doc = documents.findWithApplicationById(documentId)
                .filter(d -> Objects.equals(d.getApplication().getId(), application.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        return content(doc);
    }

    @Transactional
    public void deleteOwn(Long applicationId, Long documentId) {
        PermitApplication application = ownApplication(applicationId);
        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new ConflictException("Documents can only be removed while the application is pending");
        }
        PermitDocument doc = documents.findWithApplicationById(documentId)
                .filter(d -> Objects.equals(d.getApplication().getId(), application.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        String path = doc.getStoragePath();
        documents.delete(doc);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storage.deleteQuietly(path);
            }
        });
    }

    @Transactional(readOnly = true)
    public DocumentContent contentForAdmin(Long documentId) {
        return content(find(documentId));
    }

    @Transactional
    public DocumentResponse verify(Long documentId) {
        PermitDocument doc = find(documentId);
        if (!doc.isVerified()) {
            doc.markVerified(users.getReferenceById(CurrentUser.require().id()), clock.instant());
            audit.record(AuditAction.DOCUMENT_VERIFIED, "PermitDocument", documentId,
                    Map.of("applicationId", doc.getApplication().getId()));
        }
        return DocumentResponse.from(doc);
    }

    public boolean hasDocumentOfType(Long applicationId, DocumentType type) {
        return documents.existsByApplicationIdAndDocumentType(applicationId, type);
    }

    private DocumentContent content(PermitDocument doc) {
        return new DocumentContent(storage.load(doc.getStoragePath()), doc.getFileName(), doc.getMimeType(),
                doc.getFileSize());
    }

    private PermitDocument find(Long id) {
        return documents.findWithApplicationById(id).orElseThrow(() -> new ResourceNotFoundException("Document not found"));
    }

    /** Returns the application only if it belongs to the current tourist (404 otherwise, to avoid enumeration). */
    private PermitApplication ownApplication(Long applicationId) {
        Long touristId = touristService.currentProfile().getId();
        return applications.findByIdAndTouristId(applicationId, touristId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));
    }
}
