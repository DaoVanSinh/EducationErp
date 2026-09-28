package com.eduerp.modules.identity.dto;

import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.shared.NamedReference;
import java.util.List;
import java.util.UUID;

/** Một dòng trong bảng danh sách tài khoản của màn hình quản trị. */
public record AccountSummaryResponse(
        UUID id,
        String email,
        String fullName,
        IdentityConstants.AccountStatus status,
        String roleCode,
        UUID branchId,
        String branchName,
        List<NamedReference> groups) {
}
