package com.eduerp.modules.organization;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class BranchNotFoundException extends OrganizationException {

    public BranchNotFoundException(UUID branchId) {
        super("ORGANIZATION_BRANCH_NOT_FOUND", HttpStatus.NOT_FOUND, "Không tìm thấy chi nhánh " + branchId);
    }
}
