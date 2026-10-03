package com.eduerp.modules.payroll;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class ContractNotFoundException extends PayrollException {
    public ContractNotFoundException(UUID contractId) {
        super("PAYROLL_CONTRACT_NOT_FOUND", HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng " + contractId);
    }
}
