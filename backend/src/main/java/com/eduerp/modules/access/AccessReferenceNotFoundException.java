package com.eduerp.modules.access;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Một id được gửi lên nhưng không trỏ tới bản ghi nào (Group, PermissionGroup, Permission, Account
 * — tài khoản tham chiếu để gán role/group). errorCode ghép từ tên tài nguyên nên frontend phân
 * biệt được thiếu gì mà không cần đọc câu tiếng Việt trong message.
 */
public final class AccessReferenceNotFoundException extends AccessException {

    public AccessReferenceNotFoundException(String resource, UUID id) {
        super("ACCESS_" + resource + "_NOT_FOUND", HttpStatus.NOT_FOUND,
                "Không tìm thấy " + resource + " " + id);
    }
}
