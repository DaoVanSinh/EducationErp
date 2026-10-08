/**
 * Bootstrap Spring Security dùng chung cho toàn hệ thống: bật web security và method security
 * ({@code @PreAuthorize}/{@code @PostAuthorize}), tiêm {@code PermissionEvaluator} bất kỳ module
 * nào cung cấp. Không định nghĩa {@code SecurityFilterChain} hay biết endpoint nào cần quyền gì —
 * đó là việc của tầng {@code web} từng module (rule #4: core chỉ là cơ chế, không phải nghiệp vụ).
 */
package com.eduerp.core.security;
