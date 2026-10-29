package com.eduerp.modules.access;

import java.util.Set;
import java.util.UUID;

/**
 * Cổng cho các module nghiệp vụ tham gia vào việc gán/đổi vai trò mà access không phải import chúng
 * (Dependency Inversion, cùng kỹ thuật với {@link AccountExistenceCheck}): courses chặn bỏ năng lực
 * giáo viên khi người đó còn dạy lớp, enrollment chặn bỏ năng lực học viên khi còn đang học, students/
 * teachers tự tạo hoặc tạm ngưng hồ sơ nghiệp vụ.
 *
 * <p>Cả hai hook chạy ĐỒNG BỘ trong cùng transaction với việc gán vai trò: ném lỗi ở đây là huỷ toàn
 * bộ thao tác, không có trạng thái nửa vời.
 */
public interface RoleAssignmentParticipant {

    /** Trước khi đổi vai trò của một tài khoản ĐÃ có vai trò. Ném {@code AppException} để chặn. */
    default void beforeRoleChange(UUID accountId, Set<EffectivePermission> before, Set<EffectivePermission> after) {
    }

    /** Sau khi gán (lần đầu) hoặc đổi vai trò; {@code granted} là quyền hiệu lực mới. */
    default void afterRoleAssigned(UUID accountId, Set<EffectivePermission> granted) {
    }
}
