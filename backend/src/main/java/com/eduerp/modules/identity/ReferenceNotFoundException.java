package com.eduerp.modules.identity;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Một id được gửi lên nhưng không trỏ tới bản ghi nào. Dùng cho các tài nguyên mà request chỉ tham
 * chiếu tới (Group, Branch, PermissionGroup, Permission) — khác {@link AccountNotFoundException} là
 * tài khoản đang bị tác động.
 *
 * <p>errorCode ghép từ tên tài nguyên nên frontend phân biệt được thiếu Group hay thiếu Branch mà
 * không cần đọc câu tiếng Việt trong message.
 */
public final class ReferenceNotFoundException extends IdentityException {

    public ReferenceNotFoundException(String resource, UUID id) {
        super("IDENTITY_" + resource + "_NOT_FOUND", HttpStatus.NOT_FOUND,
                "Không tìm thấy " + resource + " " + id);
    }
}
