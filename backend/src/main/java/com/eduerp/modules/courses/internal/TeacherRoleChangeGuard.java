package com.eduerp.modules.courses.internal;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.EffectivePermission;
import com.eduerp.modules.access.RoleAssignmentParticipant;
import com.eduerp.modules.courses.ClassLifecycleException;
import com.eduerp.modules.courses.CoursesConstants;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Chặn đổi vai trò làm MẤT năng lực giảng dạy khi người đó còn được phân công dạy lớp đang mở - nếu không
 * lớp sẽ có một "giáo viên" không còn vào được cổng giảng dạy, không ai điểm danh/nhập điểm được.
 */
@Component
class TeacherRoleChangeGuard implements RoleAssignmentParticipant {

    private static final List<CoursesConstants.ClassStatus> OPEN_STATUSES = Arrays.stream(
            CoursesConstants.ClassStatus.values()).filter(CoursesConstants.ClassStatus::isOpen).toList();

    private final ClassRepository classes;

    TeacherRoleChangeGuard(ClassRepository classes) {
        this.classes = classes;
    }

    @Override
    public void beforeRoleChange(UUID accountId, Set<EffectivePermission> before, Set<EffectivePermission> after) {
        boolean wasTeacher = EffectivePermission.grants(before, AccessConstants.Resources.TEACHING,
                AccessConstants.Actions.READ);
        boolean staysTeacher = EffectivePermission.grants(after, AccessConstants.Resources.TEACHING,
                AccessConstants.Actions.READ);
        if (!wasTeacher || staysTeacher) {
            return;
        }
        long openClasses = classes.countByTeacherIdAndStatusIn(accountId, OPEN_STATUSES);
        if (openClasses > 0) {
            throw new ClassLifecycleException.TeacherHasOpenClasses(accountId, openClasses);
        }
    }
}
