package com.eduerp.modules.payroll;

import org.springframework.http.HttpStatus;

public final class InvalidContractTermsException extends PayrollException {
    public InvalidContractTermsException(String message) {
        super("PAYROLL_INVALID_CONTRACT_TERMS", HttpStatus.BAD_REQUEST, message);
    }
}
