package com.example.touristpermit.document;

import com.example.touristpermit.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Object storage against a real S3-compatible server (SeaweedFS S3 gateway). */
class S3FileStorageIT {

    static final GenericContainer<?> S3 = new GenericContainer<>("chrislusf/seaweedfs:4.50")
            .withCommand("server", "-s3", "-dir=/data")
            .withExposedPorts(8333)
            .waitingFor(Wait.forListeningPort().withStartupTimeout(java.time.Duration.ofMinutes(2)));
    static S3StorageProperties props;

    @BeforeAll
    static void start() throws Exception {
        S3.start();
        props = new S3StorageProperties("http://" + S3.getHost() + ":" + S3.getMappedPort(8333), "us-east-1",
                "yatrapass-test", "test-access", "test-secret", true, true);
        // The S3 gateway accepts connections before its volume server is ready; wait until a bucket can be created.
        long deadline = System.currentTimeMillis() + 90_000;
        while (true) {
            try {
                new S3FileStorage(props, Clock.systemUTC());
                break;
            } catch (RuntimeException e) {
                if (System.currentTimeMillis() > deadline) {
                    throw e;
                }
                Thread.sleep(1000);
            }
        }
    }

    @AfterAll
    static void stop() {
        S3.stop();
    }

    S3FileStorage storage() {
        return new S3FileStorage(props, Clock.fixed(Instant.parse("2026-11-02T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void storesLoadsAndDeletesObjects() throws Exception {
        S3FileStorage storage = storage(); // also creates the bucket on first use
        byte[] content = "%PDF-1.4 object storage test".getBytes();

        String key = storage.store(new ByteArrayInputStream(content), "pdf");
        assertThat(key).matches("2026/11/[0-9a-f-]{36}\\.pdf");

        try (InputStream in = storage.load(key).getInputStream()) {
            assertThat(in.readAllBytes()).isEqualTo(content);
        }

        storage.deleteQuietly(key);
        assertThatThrownBy(() -> storage.load(key)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void twoInstancesShareTheSameBucket() throws Exception {
        String key = storage().store(new ByteArrayInputStream(new byte[]{1, 2, 3}), "png");
        try (InputStream in = storage().load(key).getInputStream()) { // a different "instance"
            assertThat(in.readAllBytes()).containsExactly(1, 2, 3);
        }
    }

    @Test
    void rejectsKeysItDidNotGenerate() {
        S3FileStorage storage = storage();
        assertThatThrownBy(() -> storage.load("../../etc/passwd")).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> storage.load("2026/11/not-a-uuid.pdf")).isInstanceOf(SecurityException.class);
    }

    @Test
    void missingBucketFailsFastWhenCreationDisabled() {
        S3StorageProperties noCreate = new S3StorageProperties(props.endpoint(), props.region(), "does-not-exist",
                props.accessKey(), props.secretKey(), true, false);
        assertThatThrownBy(() -> new S3FileStorage(noCreate, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }
}
