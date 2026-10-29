package com.eduerp.modules.academic.internal.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.academic.ClassNotFoundException;
import com.eduerp.modules.academic.NotAssignedToClassException;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.access.DataScope;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.courses.TestClassInfos;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Review Focus #4 + #5. RBAC chỉ có {@code @PreAuthorize} thô ("có quyền ở scope X hay không"), không
 * biết "X của ai" - phần "của ai" nằm ở đây: ORGANIZATION mọi lớp, BRANCH lớp thuộc chi nhánh chủ quản
 * (spec 6.4 lớp 2), còn lại phải là chính giáo viên được phân công.
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
        when(courses.getClassInfo(classId)).thenReturn(Optional.of(classInfo()));
    }

    private CoursesManagement.ClassInfoResponse classInfo() {
        return TestClassInfos.of(classId, courseId, branchId, assignedTeacherId, 20, true, 24, List.of());
    }

    /** Người gọi có quyền GRADE (mọi action) ở {@code scope}; với BRANCH thì thuộc {@code homeBranchId}. */
    private void stubCallerScope(UUID accountId, AccessConstants.PermissionScope scope, UUID homeBranchId) {
        when(access.dataScopeOf(eq(accountId), eq(AccessConstants.Resources.GRADE), any()))
                .thenAnswer(invocation -> new DataScope(AccessConstants.Resources.GRADE, invocation.getArgument(2),
                        scope, homeBranchId));
    }

    @Test
    void allowsTheTeacherAssignedToTheClassAndReturnsTheClassInfo() {
        stubClassTaughtByAssignedTeacher();
        stubCallerScope(assignedTeacherId, AccessConstants.PermissionScope.PERSONAL, null);

        var classInfo = guard.requireAccessToClass(assignedTeacherId, classId, AccessConstants.Actions.UPDATE);

        assertThat(classInfo.classId()).isEqualTo(classId);
        assertThat(classInfo.courseId()).isEqualTo(courseId);
        assertThat(classInfo.teacherId()).isEqualTo(assignedTeacherId);
        verify(courses).getClassInfo(classId);
    }

    /** Giáo viên khác CÓ quyền GRADE:UPDATE:PERSONAL nhưng không dạy lớp này → 403. */
    @Test
    void refusesATeacherWhoHoldsThePermissionButIsNotAssignedToThisClass() {
        var otherTeacherId = UUID.randomUUID();
        stubClassTaughtByAssignedTeacher();
        stubCallerScope(otherTeacherId, AccessConstants.PermissionScope.PERSONAL, null);

        assertThatThrownBy(() -> guard.requireAccessToClass(otherTeacherId, classId, AccessConstants.Actions.UPDATE))
                .isInstanceOf(NotAssignedToClassException.class)
                .hasMessageContaining(classId.toString());
    }

    /** Spec 6.4 lớp 2: quản lý chi nhánh (scope BRANCH) thao tác được lớp của CHI NHÁNH MÌNH. */
    @Test
    void allowsABranchScopedCallerOnAClassOfTheirOwnBranch() {
        var branchManagerId = UUID.randomUUID();
        stubClassTaughtByAssignedTeacher();
        stubCallerScope(branchManagerId, AccessConstants.PermissionScope.BRANCH, branchId);

        assertThat(guard.requireAccessToClass(branchManagerId, classId, AccessConstants.Actions.UPDATE).classId())
                .isEqualTo(classId);
    }

    @Test
    void refusesABranchScopedCallerOnAClassOfAnotherBranch() {
        var branchManagerId = UUID.randomUUID();
        stubClassTaughtByAssignedTeacher();
        stubCallerScope(branchManagerId, AccessConstants.PermissionScope.BRANCH, UUID.randomUUID());

        assertThatThrownBy(() -> guard.requireAccessToClass(branchManagerId, classId, AccessConstants.Actions.UPDATE))
                .isInstanceOf(NotAssignedToClassException.class);
    }

    /** Review Focus #5: admin scope ORGANIZATION thao tác được trên lớp mình không dạy. */
    @Test
    void skipsTheAssignmentCheckForAnOrganizationScopedCaller() {
        var adminId = UUID.randomUUID();
        stubClassTaughtByAssignedTeacher();
        stubCallerScope(adminId, AccessConstants.PermissionScope.ORGANIZATION, null);

        var classInfo = guard.requireAccessToClass(adminId, classId, AccessConstants.Actions.UPDATE);

        assertThat(classInfo.teacherId())
                .as("admin vẫn nhận đúng bản ghi lớp, chỉ là không bị so với chính mình")
                .isEqualTo(assignedTeacherId);
    }

    /** Lớp không tồn tại là 404, kiểm TRƯỚC mọi chuyện về quyền - không gọi tới access làm gì. */
    @Test
    void reportsAnUnknownClassAsNotFoundWithoutAskingForPermissions() {
        when(courses.getClassInfo(classId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.requireAccessToClass(UUID.randomUUID(), classId, AccessConstants.Actions.READ))
                .isInstanceOf(ClassNotFoundException.class);
        verify(access, never()).dataScopeOf(any(), any(), any());
    }

    /** Không có quyền GRADE nào vẫn phải là 403 có nghĩa, không phải NPE thành 500. */
    @Test
    void refusesACallerWithoutAnyGradePermission() {
        var strangerId = UUID.randomUUID();
        stubClassTaughtByAssignedTeacher();
        stubCallerScope(strangerId, null, null);

        assertThatThrownBy(() -> guard.requireAccessToClass(strangerId, classId, AccessConstants.Actions.READ))
                .isInstanceOf(NotAssignedToClassException.class);
    }

    @Test
    void canAccessReturnsTrueForTheAssignedTeacher() {
        stubCallerScope(assignedTeacherId, AccessConstants.PermissionScope.PERSONAL, null);

        assertThat(guard.canAccess(assignedTeacherId, classInfo(), AccessConstants.Actions.READ)).isTrue();
    }

    @Test
    void canAccessReturnsFalseForATeacherNotAssignedToTheClass() {
        var otherTeacherId = UUID.randomUUID();
        stubCallerScope(otherTeacherId, AccessConstants.PermissionScope.PERSONAL, null);

        assertThat(guard.canAccess(otherTeacherId, classInfo(), AccessConstants.Actions.READ)).isFalse();
    }

    @Test
    void canAccessReturnsTrueForAnOrganizationScopedCallerRegardlessOfAssignment() {
        var adminId = UUID.randomUUID();
        stubCallerScope(adminId, AccessConstants.PermissionScope.ORGANIZATION, null);

        assertThat(guard.canAccess(adminId, classInfo(), AccessConstants.Actions.READ)).isTrue();
    }

    /** Hai action tách biệt: quyền READ ở ORGANIZATION không mở cửa UPDATE trên lớp người khác. */
    @Test
    void checksTheActionItWasAskedAbout() {
        var readOnlyAdminId = UUID.randomUUID();
        stubClassTaughtByAssignedTeacher();
        when(access.dataScopeOf(readOnlyAdminId, AccessConstants.Resources.GRADE, AccessConstants.Actions.READ))
                .thenReturn(new DataScope(AccessConstants.Resources.GRADE, AccessConstants.Actions.READ,
                        AccessConstants.PermissionScope.ORGANIZATION, null));
        when(access.dataScopeOf(readOnlyAdminId, AccessConstants.Resources.GRADE, AccessConstants.Actions.UPDATE))
                .thenReturn(new DataScope(AccessConstants.Resources.GRADE, AccessConstants.Actions.UPDATE, null, null));

        assertThat(guard.requireAccessToClass(readOnlyAdminId, classId, AccessConstants.Actions.READ).classId())
                .isEqualTo(classId);
        assertThatThrownBy(() -> guard.requireAccessToClass(readOnlyAdminId, classId, AccessConstants.Actions.UPDATE))
                .isInstanceOf(NotAssignedToClassException.class);
    }
}
