package com.eduerp.modules.payroll;

import java.util.UUID;

public final class PayrollEvents {

    private PayrollEvents() {
    }

    public record PayrollRunApproved(UUID payrollRunId, UUID actorAccountId, UUID actorBranchId) {
    }

    public record PayslipPaid(UUID payslipId, UUID actorAccountId, UUID actorBranchId) {
    }

    public record PayslipPaymentUndone(UUID payslipId, UUID actorAccountId, UUID actorBranchId) {
    }
}
