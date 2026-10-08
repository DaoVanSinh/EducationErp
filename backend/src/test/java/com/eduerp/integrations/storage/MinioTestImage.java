package com.eduerp.integrations.storage;

/**
 * Review finding Important #8: cả hai IT dùng MinIO ({@code StorageClientIT},
 * {@code ContractAdminControllerIT}) trước đây tự lặp lại cùng một tag ảnh ở hai nơi, và tag đó chỉ
 * chạy được nhờ cache Docker sẵn có trên máy này (Docker Hub từ chối pull mới cho mọi tag đã thử -
 * xác nhận bằng {@code docker pull} trực tiếp, và review độc lập xác nhận lại bằng cả Docker Hub lẫn
 * quay.io, kết luận nhiều khả năng là egress registry bị chặn trên máy/mạng này, không hẳn là chính
 * sách của Docker Hub). Một CI runner không có cache này sẽ pull thất bại.
 *
 * <p>Gom tag vào một hằng số duy nhất, cho phép ghi đè qua system property
 * {@code -Dtest.minio.image=...} để CI trỏ tới registry mirror/image đã xác thực mà không phải sửa
 * code - đây là phần có thể làm được từ trong code; phần còn lại (thật sự verify pull nguội, cấu hình
 * mirror/auth cho CI) là việc hạ tầng CI, ghi lại trong ledger làm việc tiếp theo.
 */
public final class MinioTestImage {

    public static final String NAME = System.getProperty("test.minio.image", "minio/minio:RELEASE.2024-11-07T00-52-20Z");

    private MinioTestImage() {
    }
}
