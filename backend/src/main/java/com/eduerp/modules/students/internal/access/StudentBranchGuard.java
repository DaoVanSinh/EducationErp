package com.eduerp.modules.students.internal.access;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.students.internal.model.StudentProfile;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Lớp 2 cho một hồ sơ học viên cụ thể: chi nhánh của học viên = chi nhánh chủ quản của tài khoản. Chỉ tra
 * chi nhánh khi người gọi KHÔNG có scope toàn tổ chức, để admin không tốn thêm truy vấn.
 */
@Component
public class StudentBranchGuard {

    private final AccessManagement access;
    private final IdentityManagement identity;

    StudentBranchGuard(AccessManagement access, IdentityManagement identity) {
        this.access = access;
        this.identity = identity;
    }

    public void require(UUID actorAccountId, StudentProfile profile, String action) {
        var scope = access.dataScopeOf(actorAccountId, AccessConstants.Resources.STUDENT, action);
        if (scope.isOrganizationWide()) {
            return;
        }
        scope.requireCovers(identity.homeBranchOf(profile.getAccountId()).orElse(null));
    }
}
