/**
 * Redis key scheme cho toàn hệ thống: mỗi module đọc/ghi Redis qua đây, không tự nối chuỗi key
 * (rule #5). Đây là named interface {@code cache} nên các module khác được phép tham chiếu.
 */
@org.springframework.modulith.NamedInterface("cache")
package com.eduerp.infra.cache;
