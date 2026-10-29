package com.eduerp.modules.access;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link AccessManagement} giả cho unit test của use case có kiểm lớp 2 (spec 6.4): mặc định người gọi có
 * scope ORGANIZATION cho mọi tài nguyên - đúng hành vi của admin, nên test viết trước khi có lọc chi nhánh
 * giữ nguyên ý nghĩa.
 */
public final class TestAccess {

    private TestAccess() {
    }

    public static AccessManagement organizationWide() {
        return withScope(AccessConstants.PermissionScope.ORGANIZATION, null);
    }

    public static AccessManagement withScope(AccessConstants.PermissionScope scope, java.util.UUID homeBranchId) {
        var access = mock(AccessManagement.class);
        when(access.dataScopeOf(any(), any(), any())).thenAnswer(invocation -> new DataScope(
                invocation.getArgument(1), invocation.getArgument(2), scope, homeBranchId));
        when(access.grants(any(), any(), any())).thenReturn(true);
        return access;
    }
}
