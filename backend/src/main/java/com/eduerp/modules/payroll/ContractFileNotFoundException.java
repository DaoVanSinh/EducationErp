package com.eduerp.modules.payroll;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class ContractFileNotFoundException extends PayrollException {
    public ContractFileNotFoundException(UUID contractId) {
        super("PAYROLL_CONTRACT_FILE_NOT_FOUND", HttpStatus.NOT_FOUND,
                "Hợp đồng " + contractId + " chưa có file đính kèm");
    }
}
