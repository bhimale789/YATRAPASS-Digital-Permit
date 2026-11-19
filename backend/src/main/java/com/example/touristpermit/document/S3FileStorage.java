package com.example.touristpermit.document;

import com.example.touristpermit.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * Object storage on AWS S3 or any S3-compatible service such as MinIO
 * ({@code STORAGE_TYPE=s3}). All backend instances share the same bucket, so uploads
 * are available regardless of which instance handles a request. Objects are private;
 * they are only ever served through the authorised API endpoints.
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3FileStorage implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(S3FileStorage.class);
    /** Only keys produced by {@link FileStorageService#newKey} are accepted. */
    private static final Pattern KEY = Pattern.compile("^\\d{4}/\\d{2}/[0-9a-f-]{36}\\.[a-z0-9]{2,5}$");

    private final S3Client s3;
    private final String bucket;
    private final Clock clock;

    public S3FileStorage(S3StorageProperties props, Clock clock) {
        this(buildClient(props), props.bucket(), props.createBucket(), clock);
    }

    S3FileStorage(S3Client s3, String bucket, boolean createBucket, Clock clock) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("S3_BUCKET must be set when STORAGE_TYPE=s3");
        }
        this.s3 = s3;
        this.bucket = bucket;
        this.clock = clock;
        ensureBucket(createBucket);
    }

    static S3Client buildClient(S3StorageProperties props) {
        AwsCredentialsProvider credentials = props.accessKey() == null || props.accessKey().isBlank()
                ? DefaultCredentialsProvider.builder().build()
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(props.accessKey(), props.secretKey()));
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(props.region() == null || props.region().isBlank() ? "us-east-1" : props.region()))
                .credentialsProvider(credentials)
                .forcePathStyle(props.pathStyle());
        if (props.endpoint() != null && !props.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(props.endpoint()));
        }
        return builder.build();
    }

    private void ensureBucket(boolean create) {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            if (!create) {
                throw new IllegalStateException("S3 bucket does not exist: " + bucket);
            }
            s3.createBucket(b -> b.bucket(bucket));
            log.info("Created storage bucket {}", bucket);
        } catch (S3Exception e) {
            if (e.statusCode() == 404 && create) {
                s3.createBucket(b -> b.bucket(bucket));
                log.info("Created storage bucket {}", bucket);
            } else {
                throw e;
            }
        }
    }

    @Override
    public String store(InputStream content, String extension) {
        String key = FileStorageService.newKey(LocalDate.now(clock), extension);
        try {
            // Uploads are size-capped (MAX_UPLOAD_SIZE), so buffering is bounded.
            byte[] bytes = content.readAllBytes();
            s3.putObject(b -> b.bucket(bucket).key(key), RequestBody.fromBytes(bytes));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read upload", e);
        }
        return key;
    }

    @Override
    public Resource load(String key) {
        requireValidKey(key);
        try {
            return new InputStreamResource(s3.getObject(b -> b.bucket(bucket).key(key)));
        } catch (NoSuchKeyException e) {
            throw new ResourceNotFoundException("Document content not found");
        }
    }

    @Override
    public void deleteQuietly(String key) {
        try {
            requireValidKey(key);
            s3.deleteObject(b -> b.bucket(bucket).key(key));
        } catch (RuntimeException e) {
            log.warn("Could not delete stored object: {}", e.getClass().getSimpleName());
        }
    }

    private static void requireValidKey(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new SecurityException("Invalid storage key");
        }
    }
}
