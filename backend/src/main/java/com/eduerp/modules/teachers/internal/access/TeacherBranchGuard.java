package com.eduerp.modules.teachers.internal.access;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.access.DataScope;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.teachers.internal.model.TeacherProfile;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Admin chi nhánh chỉ làm việc với giáo viên thuộc chi nhánh mình. Giáo viên đang dạy lớp còn mở ở chi nhánh đó cũng
 * tính, vì còn vài lớp cũ được giữ tới khi kết thúc dù giáo viên khác chi nhánh chủ quản.
 */
@Component
public class TeacherBranchGuard {

    private final AccessManagement access;
    private final IdentityManagement identity;
    private final CoursesManagement courses;

    TeacherBranchGuard(AccessManagement access, IdentityManagement identity, CoursesManagement courses) {
        this.access = access;
        this.identity = identity;
        this.courses = courses;
    }

    public void require(UUID actorAccountId, TeacherProfile profile, String action) {
        var scope = scopeOf(actorAccountId, action);
        if (scope.isOrganizationWide()) {
            return;
        }
        var homeBranch = identity.homeBranchOf(profile.getAccountId()).orElse(null);
        if (scope.covers(homeBranch)
                || courses.teacherIdsTeachingAt(scope.homeBranchId()).contains(profile.getAccountId())) {
            return;
        }
        scope.requireCovers(homeBranch);
    }

    public DataScope scopeOf(UUID actorAccountId, String action) {
        return access.dataScopeOf(actorAccountId, AccessConstants.Resources.TEACHER, action);
    }
}
