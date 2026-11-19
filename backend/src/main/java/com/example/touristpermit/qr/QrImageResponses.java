package com.example.touristpermit.qr;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** HTTP responses for QR images. QR images are bearer credentials, so they are never cached. */
public final class QrImageResponses {

    private QrImageResponses() {
    }

    public static ResponseEntity<byte[]> png(byte[] image, String filenameStem, boolean download) {
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(filenameStem + "-qr.png").build();
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(image);
    }
}
