package com.eduerp.modules.organization;

import org.springframework.http.HttpStatus;

public final class BranchCodeAlreadyExistsException extends OrganizationException {

    public BranchCodeAlreadyExistsException(String code) {
        super("ORGANIZATION_BRANCH_CODE_ALREADY_EXISTS", HttpStatus.CONFLICT, "Mã chi nhánh " + code + " đã tồn tại");
    }
}
