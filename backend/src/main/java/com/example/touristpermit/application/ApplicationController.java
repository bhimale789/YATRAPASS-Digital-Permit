package com.example.touristpermit.application;

import com.example.touristpermit.application.dto.ApplicationRequest;
import com.example.touristpermit.application.dto.ApplicationResponse;
import com.example.touristpermit.common.response.PageResponse;
import com.example.touristpermit.document.DocumentDownloads;
import com.example.touristpermit.document.DocumentService;
import com.example.touristpermit.document.DocumentType;
import com.example.touristpermit.document.dto.DocumentResponse;
import com.example.touristpermit.common.util.SortWhitelist;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/applications")
@PreAuthorize("hasRole('TOURIST')")
@Tag(name = "Tourist: Applications", description = "Submit and track permit applications")
public class ApplicationController {

    private static final Set<String> SORTABLE = Set.of("submittedAt", "requestedStartDate", "status");

    private final ApplicationService applicationService;
    private final DocumentService documentService;

    public ApplicationController(ApplicationService applicationService, DocumentService documentService) {
        this.applicationService = applicationService;
        this.documentService = documentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit a permit application", description = "Created with status PENDING. "
            + "Fails with 422 if the area is inactive, dates are invalid or exceed the area's maximum, "
            + "and 409 if it overlaps an existing open application/active permit for the same area.")
    public ApplicationResponse submit(@Valid @RequestBody ApplicationRequest request) {
        return applicationService.submit(request);
    }

    @GetMapping
    @Operation(summary = "List my applications")
    public PageResponse<ApplicationResponse> list(@RequestParam(required = false) ApplicationStatus status,
                                                  @PageableDefault(sort = "submittedAt",
                                                          direction = Sort.Direction.DESC) Pageable pageable) {
        return applicationService.listMine(status, SortWhitelist.restrict(pageable, SORTABLE));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get my application with documents")
    public ApplicationResponse get(@PathVariable Long id) {
        return applicationService.getMine(id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel my application (PENDING or UNDER_REVIEW only)")
    public ApplicationResponse cancel(@PathVariable Long id) {
        return applicationService.cancelMine(id);
    }

    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload a supporting document", description = "PDF, JPEG or PNG; size-limited; "
            + "content is verified against the declared type.")
    public DocumentResponse upload(@PathVariable Long id,
                                   @RequestParam DocumentType documentType,
                                   @RequestPart("file") MultipartFile file) {
        return documentService.upload(id, documentType, file);
    }

    @GetMapping("/{id}/documents")
    public List<DocumentResponse> documents(@PathVariable Long id) {
        return documentService.listOwn(id);
    }

    @GetMapping("/{id}/documents/{documentId}/content")
    @Operation(summary = "Download one of my documents")
    public ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long documentId) {
        return DocumentDownloads.attachment(documentService.contentForOwner(id, documentId));
    }

    @DeleteMapping("/{id}/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a document (PENDING applications only)")
    public void deleteDocument(@PathVariable Long id, @PathVariable Long documentId) {
        documentService.deleteOwn(id, documentId);
    }
}
