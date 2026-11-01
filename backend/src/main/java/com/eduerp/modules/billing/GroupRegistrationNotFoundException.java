package com.eduerp.modules.billing;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class GroupRegistrationNotFoundException extends BillingException {
    public GroupRegistrationNotFoundException(UUID groupId) {
        super("BILLING_GROUP_NOT_FOUND", HttpStatus.NOT_FOUND, "Không tìm thấy nhóm đăng ký " + groupId);
    }
}
