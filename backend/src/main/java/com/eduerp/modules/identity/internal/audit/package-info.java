/**
 * Cơ chế ghi audit: annotation {@code @Audited} đánh dấu method cần ghi, aspect ghi sau khi method
 * chạy thành công, và {@code CurrentActor} đọc người thực hiện từ {@code SecurityContext}.
 * Không quyết định nghiệp vụ nào nằm ở đây — chỉ là việc ghi lại điều đã xảy ra.
 */
package com.eduerp.modules.identity.internal.audit;
