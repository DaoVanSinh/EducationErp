/**
 * Module hạ tầng: bao bọc hệ thống ngoài (Redis, mail, storage…) để module nghiệp vụ không phụ thuộc
 * trực tiếp vào chi tiết kỹ thuật.
 *
 * <ul>
 *   <li>{@code cache} — named interface {@code cache}: toàn bộ key Redis của hệ thống (rule #5).
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Infrastructure")
package com.eduerp.infra;
