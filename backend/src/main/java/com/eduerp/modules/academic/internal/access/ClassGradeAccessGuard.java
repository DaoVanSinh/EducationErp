package com.eduerp.modules.academic.internal.access;

import com.eduerp.modules.academic.ClassNotFoundException;
import com.eduerp.modules.academic.NotAssignedToClassException;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.courses.CoursesManagement;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Nơi DUY NHẤT cài đặt luật "chỉ giáo viên được phân công dạy lớp đó (+ admin) mới thao tác được"
 * (spec mục 6). Cả năm usecase của module gọi method này - {@code RecordGrade},
 * {@code GetClassGradebook}, {@code GetClassProgress}, {@code ExportClassGradebook},
 * {@code ExportClassProgress} - để không có đường tắt nào bỏ qua kiểm tra (spec mục 7: "không có
 * đường tắt tải Excel bỏ qua kiểm tra quyền").
 *
 * <p>Vì sao luật này KHÔNG nằm trong {@code @PreAuthorize}: RBAC hiện tại chỉ trả lời "có quyền ở
 * scope X hay không", không biết "X của ai" (spec mục 6). Phát minh một loại scope mới sẽ phải sửa
 * {@code ScopedPermissionEvaluator} và toàn bộ bảng quyền; cách của spec là gác cửa thô bằng
 * {@code @PreAuthorize} rồi hỏi lại scope THẬT ở đây qua
 * {@link AccessManagement#effectivePermissions(UUID)}.
 */
@Component
public class ClassGradeAccessGuard {

    private final AccessManagement access;
    private final CoursesManagement courses;

    ClassGradeAccessGuard(AccessManagement access, CoursesManagement courses) {
        this.access = access;
        this.courses = courses;
    }

    /**
     * Xác nhận {@code actorAccountId} được phép làm {@code action} ({@code AccessConstants.Actions
     * .READ}/{@code .UPDATE}) với lớp {@code classId}, rồi TRẢ VỀ bản ghi lớp đã đọc.
     *
     * <p>Trả về {@code ClassInfoResponse} thay vì {@code void} là có chủ đích: nơi gọi cần chính bản
     * ghi đó (lịch học + số buổi chuẩn để tính tiến trình, {@code courseId} để lấy cột điểm), nên gọi
     * {@code getClassInfo} lần thứ hai là vừa thêm một câu truy vấn vừa mở ra khả năng hai lần đọc
     * lệch nhau giữa lúc kiểm quyền và lúc tính số.
     *
     * @throws ClassNotFoundException lớp không tồn tại (404) - kiểm trước mọi chuyện về quyền
     * @throws NotAssignedToClassException có quyền ở scope hẹp nhưng không phải giáo viên của lớp (403)
     */
    public CoursesManagement.ClassInfoResponse requireAccessToClass(UUID actorAccountId, UUID classId,
            String action) {
        var classInfo = courses.getClassInfo(classId)
                .orElseThrow(() -> new ClassNotFoundException(classId));
        if (!canAccess(actorAccountId, classInfo, action)) {
            throw new NotAssignedToClassException(actorAccountId, classId);
        }
        return classInfo;
    }

    /**
     * Phần điều kiện của {@link #requireAccessToClass(UUID, UUID, String)}, tách thành một hàm
     * boolean không ném. {@code GetStudentAcademicSummary} cần nó: một học viên có thể có nhiều
     * lớp, và một lớp người xem không được phép thấy phải BỎ QUA lặng lẽ, không làm hỏng cả response
     * như {@code requireAccessToClass} sẽ làm (403 toàn bộ).
     *
     * <p>Nhận sẵn {@code classInfo} thay vì {@code classId} để nơi gọi không phải tải lại bản ghi
     * lớp lần hai khi đã cần nó cho việc khác (lịch học, số buổi chuẩn).
     */
    public boolean canAccess(UUID actorAccountId, CoursesManagement.ClassInfoResponse classInfo, String action) {
        // ORGANIZATION: mọi lớp; BRANCH: lớp thuộc chi nhánh chủ quản (spec 6.4 lớp 2); còn lại phải là
        // chính giáo viên được phân công (spec mục 6 bước 4).
        if (access.dataScopeOf(actorAccountId, AccessConstants.Resources.GRADE, action).covers(classInfo.branchId())) {
            return true;
        }
        return actorAccountId.equals(classInfo.teacherId());
    }
}
