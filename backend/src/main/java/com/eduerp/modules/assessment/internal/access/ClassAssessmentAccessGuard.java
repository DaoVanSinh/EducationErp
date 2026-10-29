package com.eduerp.modules.assessment.internal.access;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.assessment.AssessmentException;
import com.eduerp.modules.courses.CoursesManagement;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Nơi DUY NHẤT cài đặt luật "ai được giao bài, nhập kết quả, soạn mini-task, xem thống kê của lớp này":
 * quyền ASSIGNMENT scope ORGANIZATION (mọi lớp), scope BRANCH (lớp thuộc chi nhánh chủ quản), hoặc chính
 * giáo viên được phân công. Cùng cách tiếp cận với {@code ClassGradeAccessGuard}.
 */
@Component
public class ClassAssessmentAccessGuard {

    private final AccessManagement access;
    private final CoursesManagement courses;

    ClassAssessmentAccessGuard(AccessManagement access, CoursesManagement courses) {
        this.access = access;
        this.courses = courses;
    }

    /**
     * @throws AssessmentException.ClassNotFound lớp không tồn tại (404)
     * @throws AssessmentException.NotAssignedToClass có quyền scope hẹp nhưng không dạy lớp này (403)
     */
    public CoursesManagement.ClassInfoResponse requireAccessToClass(UUID actorAccountId, UUID classId,
            String action) {
        var classInfo = courses.getClassInfo(classId)
                .orElseThrow(() -> new AssessmentException.ClassNotFound(classId));
        if (access.dataScopeOf(actorAccountId, AccessConstants.Resources.ASSIGNMENT, action)
                .covers(classInfo.branchId())) {
            return classInfo;
        }
        if (!actorAccountId.equals(classInfo.teacherId())) {
            throw new AssessmentException.NotAssignedToClass(actorAccountId, classId);
        }
        return classInfo;
    }
}
