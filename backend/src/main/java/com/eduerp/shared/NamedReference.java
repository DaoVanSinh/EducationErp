package com.eduerp.shared;

import java.util.UUID;

/**
 * Cặp id + tên để giao diện đổ vào dropdown hoặc hiển thị nhãn. Trước khi tách module đây là kiểu
 * riêng của identity, nhưng role, group, permission group (access) và chi nhánh (organization) đều
 * cần đúng hình dạng này — đủ ngưỡng "3+ module cần, ổn định, lệch nhau là lỗi" để đưa vào shared
 * (rule #16) thay vì mỗi module tự định nghĩa một bản trùng nhau.
 */
public record NamedReference(UUID id, String name) {
}
