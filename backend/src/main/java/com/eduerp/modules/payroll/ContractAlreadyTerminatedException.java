package com.eduerp.modules.payroll;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class ContractAlreadyTerminatedException extends PayrollException {
    public ContractAlreadyTerminatedException(UUID contractId) {
        super("PAYROLL_CONTRACT_ALREADY_TERMINATED", HttpStatus.CONFLICT, "Hợp đồng " + contractId + " đã kết thúc");
    }
}
