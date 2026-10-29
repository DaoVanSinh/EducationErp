package com.eduerp.modules.courses.internal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eduerp.core.exception.AppValidationException;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.identity.IdentityManagement;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * "Là giáo viên" = có năng lực giảng dạy ({@code TEACHING:READ}), không phải mang đúng mã vai trò - vai trò
 * tự tạo có cổng giảng dạy cũng được phân công dạy lớp.
 */
class TeacherAssignmentGuardTest {

    private final IdentityManagement identity = mock(IdentityManagement.class);
    private final AccessManagement access = mock(AccessManagement.class);
    private final TeacherAssignmentGuard guard = new TeacherAssignmentGuard(identity, access);
    private final UUID accountId = UUID.randomUUID();

    private void stubExistingAccount() {
        when(identity.summariesOf(anyCollection())).thenReturn(Map.of(accountId,
                new IdentityManagement.AccountBasicInfo(accountId, "a@eduerp.local", "A")));
    }

    private void stubTeachingCapability(boolean granted) {
        when(access.grants(accountId, AccessConstants.Resources.TEACHING, AccessConstants.Actions.READ))
                .thenReturn(granted);
    }

    @Test
    void rejectsAnAccountThatDoesNotExist() {
        when(identity.summariesOf(anyCollection())).thenReturn(Map.of());

        assertThatThrownBy(() -> guard.requireTeacher(accountId)).isInstanceOf(AppValidationException.class);
    }

    @Test
    void rejectsAnAccountWithoutTheTeachingCapability() {
        stubExistingAccount();
        stubTeachingCapability(false);

        assertThatThrownBy(() -> guard.requireTeacher(accountId)).isInstanceOf(AppValidationException.class);
    }

    @Test
    void acceptsAnyAccountWithTheTeachingCapabilityWhateverItsRoleIsCalled() {
        stubExistingAccount();
        stubTeachingCapability(true);

        assertThatCode(() -> guard.requireTeacher(accountId)).doesNotThrowAnyException();
    }
}
