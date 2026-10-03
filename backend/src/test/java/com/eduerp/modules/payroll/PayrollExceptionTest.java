package com.eduerp.modules.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class PayrollExceptionTest {

    @Test
    void contractNotFoundIsNotFound() {
        var ex = new ContractNotFoundException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("PAYROLL_CONTRACT_NOT_FOUND");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void payrollRunNotFoundIsNotFound() {
        var ex = new PayrollRunNotFoundException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("PAYROLL_RUN_NOT_FOUND");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void payslipNotFoundIsNotFound() {
        var ex = new PayslipNotFoundException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("PAYROLL_PAYSLIP_NOT_FOUND");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void contractAlreadyTerminatedIsConflict() {
        var ex = new ContractAlreadyTerminatedException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("PAYROLL_CONTRACT_ALREADY_TERMINATED");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void contractAlreadyActiveIsConflict() {
        var ex = new ContractAlreadyActiveException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("PAYROLL_CONTRACT_ALREADY_ACTIVE");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void payrollRunNotEditableIsConflict() {
        var ex = new PayrollRunNotEditableException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("PAYROLL_RUN_NOT_EDITABLE");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void payrollRunNotPendingApprovalIsConflict() {
        var ex = new PayrollRunNotPendingApprovalException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("PAYROLL_RUN_NOT_PENDING_APPROVAL");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void invalidContractTermsIsBadRequest() {
        var ex = new InvalidContractTermsException("Hợp đồng chính thức phải có baseSalary");
        assertThat(ex.getErrorCode()).isEqualTo("PAYROLL_INVALID_CONTRACT_TERMS");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void payrollRunAlreadyExistsIsConflict() {
        var ex = new PayrollRunAlreadyExistsException(2026, 1);
        assertThat(ex.getErrorCode()).isEqualTo("PAYROLL_RUN_ALREADY_EXISTS");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void contractFileNotFoundIsNotFound() {
        var ex = new ContractFileNotFoundException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("PAYROLL_CONTRACT_FILE_NOT_FOUND");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
