/**
 * Module nền tảng, kỹ thuật thuần: không biết bất kỳ khái niệm nghiệp vụ nào của các module khác
 * (rule #4). Chỉ chứa hợp đồng lỗi và xử lý lỗi HTTP chung.
 *
 * <ul>
 *   <li>{@code exception} — named interface {@code exception}: {@code AppException} là base class mà
 *       mọi module nghiệp vụ kế thừa, và {@code GlobalExceptionHandler} dịch nó thành HTTP response.
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Core")
package com.eduerp.core;
