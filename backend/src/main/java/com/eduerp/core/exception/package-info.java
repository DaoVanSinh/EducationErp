/**
 * Hợp đồng lỗi dùng chung. Là named interface {@code exception} nên module nghiệp vụ được phép kế
 * thừa {@code AppException} — đây là điểm mở rộng duy nhất của {@code core} ra ngoài.
 */
@org.springframework.modulith.NamedInterface("exception")
package com.eduerp.core.exception;
