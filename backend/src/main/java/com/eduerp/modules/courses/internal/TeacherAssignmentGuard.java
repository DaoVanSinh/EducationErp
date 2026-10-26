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
 * <p>Spec Phân hệ 2 mục 3 để ngỏ kiểm tra role ("out of scope ở phase này") vì khi đó chưa có hồ sơ
 * giáo viên. Từ Phân hệ 2.5 role TEACHER đã có nghĩa nghiệp vụ rõ ràng và cổng giảng dạy (TEACHING)
 * chỉ cấp cho role đó - giao lớp cho một tài khoản ADMIN/STUDENT sẽ tạo ra một lớp không ai nhập điểm
 * được, nên chặn ngay tại đây.
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
        var isTeacher = access.roleOf(teacherId)
                .map(role -> AccessConstants.RoleCodes.TEACHER.equals(role.code()))
                .orElse(false);
        if (!isTeacher) {
            throw new AppValidationException(FIELD, "Tài khoản được chọn không có vai trò Giáo viên");
        }
    }
}
