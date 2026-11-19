package com.example.touristpermit.document;

import com.example.touristpermit.document.dto.DocumentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/documents")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: Documents")
public class AdminDocumentController {

    private final DocumentService documentService;

    public AdminDocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/{id}/content")
    @Operation(summary = "Download a submitted document")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        return DocumentDownloads.attachment(documentService.contentForAdmin(id));
    }

    @PostMapping("/{id}/verify")
    @Operation(summary = "Mark a document as verified")
    public DocumentResponse verify(@PathVariable Long id) {
        return documentService.verify(id);
    }
}
