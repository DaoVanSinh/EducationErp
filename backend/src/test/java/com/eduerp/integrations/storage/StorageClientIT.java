package com.eduerp.integrations.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.redis.testcontainers.RedisContainer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

@Testcontainers
@SpringBootTest
class StorageClientIT {

    private static final String BUCKET = "eduerp-test";

    // Bắt buộc có Postgres + Redis riêng: thiếu chúng, context này nối vào địa chỉ mặc định localhost:5433 và
    // localhost:6379 - chính CSDL và Redis của stack đang chạy trên máy - và Flyway sẽ áp migration mới lên đó.
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    // Docker Hub từ chối pull mọi tag minio/minio mới (kể cả tag được plan gốc chọn) - "pull access
    // denied", xác nhận bằng docker pull trực tiếp. Dùng đúng tag đã có sẵn trong cache Docker local
    // (image agent-minio dùng chung của máy này đang chạy bằng tag này) để không cần pull mạng.
    @Container
    static MinIOContainer minio = new MinIOContainer(MinioTestImage.NAME);

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("storage.endpoint", minio::getS3URL);
        registry.add("storage.region", () -> "us-east-1");
        registry.add("storage.bucket", () -> BUCKET);
        registry.add("storage.access-key", minio::getUserName);
        registry.add("storage.secret-key", minio::getPassword);
    }

    @BeforeAll
    static void createBucket() {
        var client = S3Client.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create(minio.getS3URL()))
                .forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(minio.getUserName(), minio.getPassword())))
                .build();
        client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
    }

    @Autowired
    StorageClient storageClient;

    @Test
    void uploadsDownloadsAndDeletesAFile() {
        var content = "ảnh đại diện giả lập".getBytes(StandardCharsets.UTF_8);

        var key = storageClient.upload("avatars/test", "avatar.png", content, "image/png");
        var downloaded = storageClient.download(key);
        storageClient.delete(key);

        assertThat(downloaded).isEqualTo(content);
    }
}
