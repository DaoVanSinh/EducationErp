package com.eduerp.modules.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class PayrollExceptionTest {

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
    void payrollRunAlreadyExistsIsConflict() {
        var ex = new PayrollRunAlreadyExistsException(2026, 1);
        assertThat(ex.getErrorCode()).isEqualTo("PAYROLL_RUN_ALREADY_EXISTS");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void periodInFutureIsBadRequest() {
        var ex = new PayrollPeriodInFutureException(YearMonth.of(2099, 1));
        assertThat(ex.getErrorCode()).isEqualTo("PAYROLL_PERIOD_IN_FUTURE");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void periodNotEndedIsConflict() {
        var ex = new PayrollPeriodNotEndedException(LocalDate.of(2026, 11, 30));
        assertThat(ex.getErrorCode()).isEqualTo("PAYROLL_PERIOD_NOT_ENDED");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void missingPayTermsIsConflict() {
        var ex = new PayrollRunMissingPayTermsException(2);
        assertThat(ex.getErrorCode()).isEqualTo("PAYROLL_MISSING_PAY_TERMS");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }
}
