package com.eduerp.modules.attendance.internal.access;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.attendance.AttendanceException;
import com.eduerp.modules.courses.CoursesManagement;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Nơi DUY NHẤT cài đặt luật "ai được điểm danh lớp này": quyền ATTENDANCE scope ORGANIZATION (mọi lớp),
 * scope BRANCH (lớp thuộc chi nhánh chủ quản - spec 6.4 lớp 2), hoặc chính giáo viên được phân công.
 * {@code @PreAuthorize} chỉ gác cửa thô; ở đây hỏi lại scope THẬT rồi so chi nhánh/{@code teacherId}.
 */
@Component
public class ClassAttendanceAccessGuard {

    private final AccessManagement access;
    private final CoursesManagement courses;

    ClassAttendanceAccessGuard(AccessManagement access, CoursesManagement courses) {
        this.access = access;
        this.courses = courses;
    }

    /**
     * @throws AttendanceException.ClassNotFound lớp không tồn tại (404)
     * @throws AttendanceException.NotAssignedToClass có quyền scope hẹp nhưng không dạy lớp này (403)
     */
    public CoursesManagement.ClassInfoResponse requireAccessToClass(UUID actorAccountId, UUID classId,
            String action) {
        var classInfo = courses.getClassInfo(classId)
                .orElseThrow(() -> new AttendanceException.ClassNotFound(classId));
        if (access.dataScopeOf(actorAccountId, AccessConstants.Resources.ATTENDANCE, action)
                .covers(classInfo.branchId())) {
            return classInfo;
        }
        if (!actorAccountId.equals(classInfo.teacherId())) {
            throw new AttendanceException.NotAssignedToClass(actorAccountId, classId);
        }
        return classInfo;
    }
}
