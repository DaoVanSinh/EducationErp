package com.eduerp.modules.payroll;

import com.eduerp.core.exception.AppException;
import org.springframework.http.HttpStatus;

/** Gốc của mọi lỗi nghiệp vụ do module payroll phát ra. */
public sealed class PayrollException extends AppException
        permits ContractNotFoundException, PayrollRunNotFoundException, PayslipNotFoundException,
        ContractAlreadyTerminatedException, ContractAlreadyActiveException, PayrollRunNotEditableException,
        PayrollRunNotPendingApprovalException, InvalidContractTermsException, PayrollRunAlreadyExistsException,
        ContractFileNotFoundException {

    protected PayrollException(String errorCode, HttpStatus status, String message) {
        super(errorCode, status, message);
    }
}
