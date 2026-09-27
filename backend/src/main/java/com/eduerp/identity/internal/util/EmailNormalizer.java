package com.eduerp.identity.internal.util;

import java.util.Locale;

/**
 * Đưa email về dạng chuẩn duy nhất để lưu và để tra cứu.
 *
 * <p>Postgres so sánh {@code VARCHAR} có phân biệt hoa thường, nên {@code UNIQUE} trên cột email
 * không chặn được {@code a@x.com} và {@code A@x.com} cùng tồn tại — hai tài khoản cho một người.
 * Chuẩn hoá ở một chỗ duy nhất là cách đóng cả hai lỗ: trùng tài khoản khi ghi, và đăng nhập
 * thất bại vì gõ khác hoa thường khi đọc.
 *
 * <p>Chỉ hạ hoa thường và cắt khoảng trắng hai đầu. Không cắt dấu chấm hay phần {@code +tag} của
 * Gmail: đó là chính sách của từng nhà cung cấp, không phải chuẩn email, và áp vào đây sẽ vô tình
 * gộp hai email hợp lệ khác nhau thành một.
 */
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    public static String normalize(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
