package com.example.touristpermit.permit;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** A rendered permit printout. It may embed a live QR code (a bearer credential), so it is never cached. */
public record PermitPdf(String permitNumber, byte[] content) {

    public ResponseEntity<byte[]> toResponse(boolean download) {
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(permitNumber + ".pdf").build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(content);
    }
}
