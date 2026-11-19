package com.example.touristpermit.document;

import org.springframework.core.io.Resource;

public record DocumentContent(Resource resource, String fileName, String mimeType, long size) {
}
