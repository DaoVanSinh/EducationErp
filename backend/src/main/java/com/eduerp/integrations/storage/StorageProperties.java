package com.eduerp.integrations.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** endpoint trống = AWS S3 thật (prod); có giá trị = MinIO/S3-compatible tự host (dev) - spec mục 3.5. */
@ConfigurationProperties(prefix = "storage")
public record StorageProperties(String endpoint, String region, String bucket, String accessKey, String secretKey) {
}
