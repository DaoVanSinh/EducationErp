package com.eduerp.modules.payroll;

import java.util.UUID;

public final class PayrollEvents {

    private PayrollEvents() {
    }

    public record ContractCreated(UUID contractId, UUID actorAccountId, UUID actorBranchId) {
    }

    public record ContractTerminated(UUID contractId, UUID actorAccountId, UUID actorBranchId) {
    }

    public record PayrollRunApproved(UUID payrollRunId, UUID actorAccountId, UUID actorBranchId) {
    }
}
