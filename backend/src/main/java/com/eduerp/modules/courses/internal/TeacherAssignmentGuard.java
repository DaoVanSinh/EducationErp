package com.eduerp.modules.courses.internal;

import com.eduerp.core.exception.AppValidationException;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.identity.IdentityManagement;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Chốt "người được phân công dạy lớp phải là giáo viên" cho cả mở lớp lẫn sửa lớp - một chỗ duy
 * nhất thay vì lặp hai lần trong {@code CreateClass}/{@code UpdateClass}.
 *
 * <p>"Là giáo viên" = có NĂNG LỰC giảng dạy (cổng giảng dạy {@code TEACHING:READ}), không phải mang đúng
 * mã vai trò {@code TEACHER}: vai trò tự tạo như "Giáo viên bán thời gian" cũng được phân công dạy nếu admin
 * cấp cho nó cổng giảng dạy (rbac.md: permissions over roles). Giao lớp cho người không có cổng giảng dạy
 * sẽ tạo ra một lớp không ai điểm danh/nhập điểm được, nên chặn ngay tại đây.
 */
@Component
public class TeacherAssignmentGuard {

    private static final String FIELD = "teacherId";

    private final IdentityManagement identity;
    private final AccessManagement access;

    TeacherAssignmentGuard(IdentityManagement identity, AccessManagement access) {
        this.identity = identity;
        this.access = access;
    }

    public void requireTeacher(UUID teacherId) {
        if (!identity.summariesOf(List.of(teacherId)).containsKey(teacherId)) {
            throw new AppValidationException(FIELD, "Giáo viên không tồn tại");
        }
        if (!access.grants(teacherId, AccessConstants.Resources.TEACHING, AccessConstants.Actions.READ)) {
            throw new AppValidationException(FIELD, "Tài khoản được chọn không có năng lực giảng dạy");
        }
    }
}
