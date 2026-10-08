package com.eduerp.integrations.storage;

import java.net.URI;
import java.util.UUID;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** Cơ chế thuần: không biết nội dung/ý nghĩa nghiệp vụ của bất kỳ file nào - mirror MailClient. */
@Component
public class StorageClient {

    private final S3Client s3Client;
    private final StorageProperties properties;

    StorageClient(StorageProperties properties) {
        this.properties = properties;
        var builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(resolveCredentialsProvider(properties));
        if (properties.endpoint() != null && !properties.endpoint().isBlank()) {
            // MinIO (dev) cần endpoint riêng + path-style; AWS S3 thật (prod) để trống endpoint, dùng
            // virtual-hosted-style mặc định của SDK - cùng một đoạn code chạy đúng cả hai môi trường.
            builder.endpointOverride(URI.create(properties.endpoint())).forcePathStyle(true);
        }
        this.s3Client = builder.build();
    }

    /**
     * AccessKey/secretKey trống = để SDK tự lấy credentials theo chuỗi mặc định (biến môi trường,
     * profile, IAM role trên EC2/ECS...) - đúng cách AWS thật thường chạy production, và cũng tránh
     * {@code AwsBasicCredentials.create} ném NullPointerException ngay khi app khởi động mà chưa cấu
     * hình storage (mọi test/@SpringBootTest khác không liên quan tới payroll cũng phải khởi động
     * được bean này).
     */
    private static AwsCredentialsProvider resolveCredentialsProvider(StorageProperties properties) {
        if (properties.accessKey() == null || properties.accessKey().isBlank()) {
            return DefaultCredentialsProvider.create();
        }
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
    }

    public String upload(String keyPrefix, String fileName, byte[] content, String contentType) {
        var key = keyPrefix + "/" + UUID.randomUUID() + "-" + fileName;
        s3Client.putObject(
                PutObjectRequest.builder().bucket(properties.bucket()).key(key).contentType(contentType).build(),
                RequestBody.fromBytes(content));
        return key;
    }

    public byte[] download(String key) {
        return s3Client.getObjectAsBytes(GetObjectRequest.builder().bucket(properties.bucket()).key(key).build())
                .asByteArray();
    }

    public void delete(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(key).build());
    }
}
