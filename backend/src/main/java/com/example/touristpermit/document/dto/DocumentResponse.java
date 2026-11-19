package com.example.touristpermit.document.dto;

import com.example.touristpermit.document.DocumentType;
import com.example.touristpermit.document.PermitDocument;

import java.time.Instant;

/** Document metadata; storage paths are never exposed. */
public record DocumentResponse(
        Long id,
        DocumentType documentType,
        String fileName,
        String mimeType,
        long fileSize,
        Instant uploadedAt,
        boolean verified,
        Instant verifiedAt
) {

    public static DocumentResponse from(PermitDocument d) {
        return new DocumentResponse(d.getId(), d.getDocumentType(), d.getFileName(), d.getMimeType(),
                d.getFileSize(), d.getUploadedAt(), d.isVerified(), d.getVerifiedAt());
    }
}
