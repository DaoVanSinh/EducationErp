package com.eduerp.modules.identity.usecase;

import com.eduerp.core.exception.AppValidationException;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.identity.dto.CreateAccountRequest;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tạo tài khoản ở màn Tài khoản. Giáo viên phải qua màn Giáo viên để nhập đủ chi nhánh, môn dạy và lương. Vai trò
 * Quản trị viên chỉ dành cho admin gốc nên không gán được ở đây, và quản trị chi nhánh bắt buộc thuộc một chi nhánh.
 */
@Service
public class CreateStaffAccount {

    private static final String ROLE_FIELD = "roleId";

    private final CreateAccount createAccount;
    private final AccessManagement access;

    CreateStaffAccount(CreateAccount createAccount, AccessManagement access) {
        this.createAccount = createAccount;
        this.access = access;
    }

    @Transactional
    public UUID execute(UUID actorAccountId, UUID actorBranchId, CreateAccountRequest request) {
        var roleCode = access.roleCodeOf(request.roleId());
        if (AccessConstants.RoleCodes.ADMIN.equals(roleCode)) {
            throw new AppValidationException(ROLE_FIELD, "Vai trò Quản trị viên chỉ dành cho admin gốc");
        }
        if (access.roleGrants(request.roleId(), AccessConstants.Resources.TEACHING, AccessConstants.Actions.READ)) {
            throw new AppValidationException(ROLE_FIELD,
                    "Tạo giáo viên ở màn Giáo viên để nhập đủ chi nhánh, môn dạy và lương");
        }
        if (AccessConstants.RoleCodes.BRANCH_ADMIN.equals(roleCode) && request.homeBranchId() == null) {
            throw new AppValidationException("homeBranchId", "Quản trị chi nhánh phải thuộc một chi nhánh");
        }
        return createAccount.execute(actorAccountId, actorBranchId, request);
    }
}
