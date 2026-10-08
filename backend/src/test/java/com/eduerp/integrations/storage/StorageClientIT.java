package com.eduerp.integrations.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

@Testcontainers
@SpringBootTest
class StorageClientIT {

    private static final String BUCKET = "eduerp-test";

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
        var content = "hợp đồng pdf giả lập".getBytes(StandardCharsets.UTF_8);

        var key = storageClient.upload("contracts/test", "contract.pdf", content, "application/pdf");
        var downloaded = storageClient.download(key);
        storageClient.delete(key);

        assertThat(downloaded).isEqualTo(content);
    }
}
