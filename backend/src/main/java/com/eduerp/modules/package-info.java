/**
 * Nơi chứa toàn bộ domain module. Bản thân package này KHÔNG phải một module — nó chỉ là chỗ gom,
 * để mở repo ra là thấy ngay hệ thống có những nghiệp vụ nào, tách biệt hẳn với {@code core}
 * (mechanism) và {@code integrations} (hệ thống ngoài).
 *
 * <p>Mỗi sub-package ở đây là một application module thật, tự khai báo bằng
 * {@code @ApplicationModule} — xem {@code spring.modulith.detection-strategy} trong
 * {@code application.yml}. Thêm domain mới mà quên annotation thì nó không phải module, và
 * {@code ModularityTests} sẽ báo thiếu.
 */
package com.eduerp.modules;
