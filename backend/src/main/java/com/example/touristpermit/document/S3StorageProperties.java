package com.example.touristpermit.document;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3-compatible object storage settings ({@code STORAGE_TYPE=s3}). Works with AWS S3
 * and MinIO. When the access key is blank, the AWS default credential chain is used
 * (e.g. an instance role).
 */
@ConfigurationProperties(prefix = "app.storage.s3")
public record S3StorageProperties(
        String endpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey,
        boolean pathStyle,
        boolean createBucket
) {
}
