package com.example.touristpermit.document;

import com.example.touristpermit.application.PermitApplication;
import com.example.touristpermit.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "permit_documents")
public class PermitDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, updatable = false)
    private PermitApplication application;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private DocumentType documentType;

    /** Sanitized original file name, for display only. */
    @Column(name = "file_name", nullable = false)
    private String fileName;

    /** Randomized path relative to the storage root. */
    @Column(name = "storage_path", nullable = false, unique = true)
    private String storagePath;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @Column(nullable = false)
    private boolean verified;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by")
    private User verifiedBy;

    protected PermitDocument() {
    }

    public PermitDocument(PermitApplication application, DocumentType documentType, String fileName,
                          String storagePath, String mimeType, long fileSize, Instant uploadedAt) {
        this.application = application;
        this.documentType = documentType;
        this.fileName = fileName;
        this.storagePath = storagePath;
        this.mimeType = mimeType;
        this.fileSize = fileSize;
        this.uploadedAt = uploadedAt;
    }

    public void markVerified(User verifier, Instant at) {
        this.verified = true;
        this.verifiedBy = verifier;
        this.verifiedAt = at;
    }

    public Long getId() {
        return id;
    }

    public PermitApplication getApplication() {
        return application;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public String getFileName() {
        return fileName;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public boolean isVerified() {
        return verified;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public User getVerifiedBy() {
        return verifiedBy;
    }
}
