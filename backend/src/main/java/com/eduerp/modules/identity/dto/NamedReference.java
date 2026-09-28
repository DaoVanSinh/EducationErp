package com.eduerp.modules.identity.dto;

import java.util.UUID;

/**
 * Cặp id + tên để giao diện đổ vào dropdown hoặc hiển thị nhãn. Dùng chung cho role, group, chi
 * nhánh: đều là "chọn một thứ đã tồn tại", không màn hình nào cần nhiều hơn hai trường này.
 */
public record NamedReference(UUID id, String name) {
}
