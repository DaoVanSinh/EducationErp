/**
 * Lưu trữ file qua S3-compatible object storage (AWS S3 thật ở production, MinIO tự host ở dev).
 * Cơ chế thuần: không biết nội dung/ý nghĩa nghiệp vụ của bất kỳ file nào - module nghiệp vụ
 * (modules.payroll) tự đặt key, gọi StorageClient.upload/download/delete.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Object Storage")
package com.eduerp.integrations.storage;
