package com.eduerp.modules.payroll.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.payroll.PayrollConstants;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Hợp đồng dữ liệu không có hành vi - test này khoá chặt field order/kiểu, vì nhiều task sau
 * (usecase, controller, frontend) đều phải xây đúng record này. */
class PayrollDtoTest {

    @Test
    void contractResponseExposesAllPayslipAndContractFields() {
        var response = new ContractResponse(UUID.randomUUID(), UUID.randomUUID(), "Nguyễn Văn A", "a@eduerp.local",
                PayrollConstants.ContractType.OFFICIAL, PayrollConstants.ContractStatus.ACTIVE,
                new BigDecimal("10000000"), null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28),
                LocalDate.of(2026, 1, 1), null, List.of(new AllowanceResponse("Xăng xe", new BigDecimal("500000"))),
                "contracts/key.pdf");

        assertThat(response.accountFullName()).isEqualTo("Nguyễn Văn A");
        assertThat(response.allowances()).hasSize(1);
    }

    @Test
    void payslipResponseExposesComputedNetPayAndInProbation() {
        var response = new PayslipResponse(UUID.randomUUID(), UUID.randomUUID(), "B", PayrollConstants.ContractType.COLLABORATOR,
                new BigDecimal("1500000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("1500000"), new BigDecimal("10"), false);

        assertThat(response.netPay()).isEqualTo(new BigDecimal("1500000"));
        assertThat(response.inProbation()).isFalse();
    }

    @Test
    void payrollRunDetailResponseBundlesRunAndPayslips() {
        var run = new PayrollRunResponse(UUID.randomUUID(), 2026, 1, PayrollConstants.PayrollRunStatus.DRAFT, 0,
                BigDecimal.ZERO);
        var detail = new PayrollRunDetailResponse(run, List.of());

        assertThat(detail.run().year()).isEqualTo(2026);
        assertThat(detail.payslips()).isEmpty();
    }
}
