package com.eduerp.modules.academic.internal.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.academic.ClassNotFoundException;
import com.eduerp.modules.academic.NotAssignedToClassException;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.access.EffectivePermission;
import com.eduerp.modules.courses.CoursesManagement;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Review Focus #4 + #5. Cơ chế này là mới trong dự án: RBAC hiện tại chỉ có {@code @PreAuthorize}
 * thô ("có quyền ở scope X hay không"), không biết "X của ai" - nên phần "của ai" nằm ở đây, trong
 * usecase layer, chứ không phát minh một loại scope RBAC mới (spec mục 6 + mục 12).
 */
class ClassGradeAccessGuardTest {

    private final AccessManagement access = mock(AccessManagement.class);
    private final CoursesManagement courses = mock(CoursesManagement.class);
    private final ClassGradeAccessGuard guard = new ClassGradeAccessGuard(access, courses);

    private final UUID classId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();
    private final UUID assignedTeacherId = UUID.randomUUID();

    private void stubClassTaughtByAssignedTeacher() {
        when(courses.getClassInfo(classId)).thenReturn(Optional.of(
                new CoursesManagement.ClassInfoResponse(classId, courseId, branchId, assignedTeacherId,
                        20, true, 24, List.of())));
    }

    private void stubCallerPermissions(UUID accountId, AccessConstants.PermissionScope scope) {
        when(access.effectivePermissions(accountId)).thenReturn(Set.of(
                new EffectivePermission(AccessConstants.Resources.GRADE, AccessConstants.Actions.UPDATE, scope),
                new EffectivePermission(AccessConstants.Resources.GRADE, AccessConstants.Actions.READ, scope)));
    }

    /** Review Focus #4, chiều hợp lệ: đúng giáo viên của lớp thao tác được, và nhận lại chính bản ghi
     * lớp để không phải gọi facade lần hai. */
    @Test
    void allowsTheTeacherAssignedToTheClassAndReturnsTheClassInfo() {
        stubClassTaughtByAssignedTeacher();
        stubCallerPermissions(assignedTeacherId, AccessConstants.PermissionScope.PERSONAL);

        var classInfo = guard.requireAccessToClass(assignedTeacherId, classId,
                AccessConstants.Actions.UPDATE);

        assertThat(classInfo.classId()).isEqualTo(classId);
        assertThat(classInfo.courseId()).isEqualTo(courseId);
        assertThat(classInfo.teacherId()).isEqualTo(assignedTeacherId);
        verify(courses).getClassInfo(classId);
    }

    /**
     * Review Focus #4, chiều bị chặn: giáo viên khác CÓ quyền {@code GRADE:UPDATE:PERSONAL} nói
     * chung nhưng không dạy lớp này → 403 {@code NotAssignedToClassException}. Đây là lỗ hổng mà
     * {@code @PreAuthorize} một mình không bao giờ bắt được.
     */
    @Test
    void refusesATeacherWhoHoldsThePermissionButIsNotAssignedToThisClass() {
        var otherTeacherId = UUID.randomUUID();
        stubClassTaughtByAssignedTeacher();
        stubCallerPermissions(otherTeacherId, AccessConstants.PermissionScope.PERSONAL);

        assertThatThrownBy(() -> guard.requireAccessToClass(otherTeacherId, classId,
                AccessConstants.Actions.UPDATE))
                .isInstanceOf(NotAssignedToClassException.class)
                .hasMessageContaining(classId.toString());
    }

    /** Spec mục 6 bước 4 nói rõ BRANCH cũng phải qua bước so teacherId, không chỉ PERSONAL. */
    @Test
    void refusesABranchScopedCallerWhoIsNotTheAssignedTeacher() {
        var branchManagerId = UUID.randomUUID();
        stubClassTaughtByAssignedTeacher();
        stubCallerPermissions(branchManagerId, AccessConstants.PermissionScope.BRANCH);

        assertThatThrownBy(() -> guard.requireAccessToClass(branchManagerId, classId,
                AccessConstants.Actions.UPDATE))
                .isInstanceOf(NotAssignedToClassException.class);
    }

    /** Review Focus #5: admin scope ORGANIZATION thao tác được trên lớp mình không dạy. */
    @Test
    void skipsTheAssignmentCheckForAnOrganizationScopedCaller() {
        var adminId = UUID.randomUUID();
        stubClassTaughtByAssignedTeacher();
        stubCallerPermissions(adminId, AccessConstants.PermissionScope.ORGANIZATION);

        var classInfo = guard.requireAccessToClass(adminId, classId, AccessConstants.Actions.UPDATE);

        assertThat(classInfo.teacherId())
                .as("admin vẫn nhận đúng bản ghi lớp, chỉ là không bị so với chính mình")
                .isEqualTo(assignedTeacherId);
    }

    /** Lớp không tồn tại là 404, kiểm TRƯỚC mọi chuyện về quyền - không gọi tới access làm gì. */
    @Test
    void reportsAnUnknownClassAsNotFoundWithoutAskingForPermissions() {
        when(courses.getClassInfo(classId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.requireAccessToClass(UUID.randomUUID(), classId,
                AccessConstants.Actions.READ))
                .isInstanceOf(ClassNotFoundException.class);
        verify(access, never()).effectivePermissions(org.mockito.ArgumentMatchers.any());
    }

    /** Caller không có quyền GRADE nào (cấu hình RBAC lệch, hoặc quyền bị thu hồi giữa phiên) vẫn
     * phải là 403 có nghĩa, không phải NPE thành 500. */
    @Test
    void refusesACallerWithoutAnyGradePermission() {
        var strangerId = UUID.randomUUID();
        stubClassTaughtByAssignedTeacher();
        when(access.effectivePermissions(strangerId)).thenReturn(Set.of());

        assertThatThrownBy(() -> guard.requireAccessToClass(strangerId, classId,
                AccessConstants.Actions.READ))
                .isInstanceOf(NotAssignedToClassException.class);
    }

    /**
     * {@code canAccess} là phần điều kiện của {@code requireAccessToClass}, tách ra thành một hàm
     * boolean không ném - dùng cho {@code GetStudentAcademicSummary}, nơi một học viên có thể có
     * nhiều lớp và một lớp không được phép xem phải bị BỎ QUA lặng lẽ, không làm hỏng cả response.
     */
    @Test
    void canAccessReturnsTrueForTheAssignedTeacher() {
        stubClassTaughtByAssignedTeacher();
        stubCallerPermissions(assignedTeacherId, AccessConstants.PermissionScope.PERSONAL);
        var classInfo = new CoursesManagement.ClassInfoResponse(classId, courseId, branchId,
                assignedTeacherId, 20, true, 24, List.of());

        assertThat(guard.canAccess(assignedTeacherId, classInfo, AccessConstants.Actions.READ)).isTrue();
    }

    @Test
    void canAccessReturnsFalseForATeacherNotAssignedToTheClass() {
        var otherTeacherId = UUID.randomUUID();
        stubCallerPermissions(otherTeacherId, AccessConstants.PermissionScope.PERSONAL);
        var classInfo = new CoursesManagement.ClassInfoResponse(classId, courseId, branchId,
                assignedTeacherId, 20, true, 24, List.of());

        assertThat(guard.canAccess(otherTeacherId, classInfo, AccessConstants.Actions.READ)).isFalse();
    }

    @Test
    void canAccessReturnsTrueForAnOrganizationScopedCallerRegardlessOfAssignment() {
        var adminId = UUID.randomUUID();
        stubCallerPermissions(adminId, AccessConstants.PermissionScope.ORGANIZATION);
        var classInfo = new CoursesManagement.ClassInfoResponse(classId, courseId, branchId,
                assignedTeacherId, 20, true, 24, List.of());

        assertThat(guard.canAccess(adminId, classInfo, AccessConstants.Actions.READ)).isTrue();
    }

    /** Hai action tách biệt: quyền READ ở ORGANIZATION không mở cửa UPDATE trên lớp người khác. */
    @Test
    void checksTheActionItWasAskedAbout() {
        var readOnlyAdminId = UUID.randomUUID();
        stubClassTaughtByAssignedTeacher();
        when(access.effectivePermissions(readOnlyAdminId)).thenReturn(Set.of(
                new EffectivePermission(AccessConstants.Resources.GRADE, AccessConstants.Actions.READ,
                        AccessConstants.PermissionScope.ORGANIZATION)));

        assertThat(guard.requireAccessToClass(readOnlyAdminId, classId, AccessConstants.Actions.READ)
                .classId()).isEqualTo(classId);
        assertThatThrownBy(() -> guard.requireAccessToClass(readOnlyAdminId, classId,
                AccessConstants.Actions.UPDATE))
                .isInstanceOf(NotAssignedToClassException.class);
    }
}
