package com.eduerp.shared;

import java.util.UUID;

/**
 * Danh tính của người gọi sau khi filter xác thực xong — gắn vào {@code Authentication}. Trước khi
 * tách module đây là kiểu riêng của identity, nhưng mọi controller ở bất kỳ module nào cần biết
 * "ai đang gọi" đều phải đọc qua {@code @AuthenticationPrincipal AccountPrincipal} — identity vẫn
 * là nơi DUY NHẤT tạo ra giá trị này (trong {@code CookieAuthenticationFilter}), {@code shared} chỉ
 * là nơi kiểu dữ liệu này được công bố để mọi module đọc mà không tạo cạnh phụ thuộc ngược lại
 * identity (rule #16).
 */
public record AccountPrincipal(UUID accountId, UUID homeBranchId) {
}
