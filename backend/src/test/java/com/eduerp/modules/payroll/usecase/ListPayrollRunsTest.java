package com.eduerp.modules.payroll.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.internal.model.PayrollRun;
import com.eduerp.modules.payroll.internal.repository.PayrollRunAggregateRow;
import com.eduerp.modules.payroll.internal.repository.PayrollRunRepository;
import com.eduerp.modules.payroll.internal.repository.PayslipRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/** Review finding Important #6: một query gộp cho cả trang, không phải một query riêng cho từng kỳ
 * lương (N+1 - 21 query cho một trang 20 dòng). */
class ListPayrollRunsTest {

    private final PayrollRunRepository runs = mock(PayrollRunRepository.class);
    private final PayslipRepository payslips = mock(PayslipRepository.class);
    private final ListPayrollRuns useCase = new ListPayrollRuns(runs, payslips);

    @Test
    void callsTheBatchAggregateQueryExactlyOnceRegardlessOfPageSize() {
        var runWithPayslips = new PayrollRun(2026, 1);
        var pageable = PageRequest.of(0, 20);
        when(runs.findAll(pageable)).thenReturn(new PageImpl<>(List.of(runWithPayslips), pageable, 1));
        var aggregateRow = mock(PayrollRunAggregateRow.class);
        when(aggregateRow.getPayrollRunId()).thenReturn(runWithPayslips.getId());
        when(aggregateRow.getPayslipCount()).thenReturn(2L);
        when(aggregateRow.getTotalGrossPay()).thenReturn(new BigDecimal("13000000"));
        when(payslips.aggregateByPayrollRunIds(any())).thenReturn(List.of(aggregateRow));

        var response = useCase.execute(pageable);

        verify(payslips, times(1)).aggregateByPayrollRunIds(any());
        var withPayslips = response.items().get(0);
        assertThat(withPayslips.payslipCount()).isEqualTo(2);
        assertThat(withPayslips.totalGrossPay()).isEqualByComparingTo(new BigDecimal("13000000"));
    }

    @Test
    void aPayrollRunWithNoAggregateRowDefaultsToZero() {
        var emptyRun = new PayrollRun(2026, 3);
        var pageable = PageRequest.of(0, 20);
        when(runs.findAll(pageable)).thenReturn(new PageImpl<>(List.of(emptyRun), pageable, 1));
        when(payslips.aggregateByPayrollRunIds(any())).thenReturn(List.of());

        var response = useCase.execute(pageable);

        var result = response.items().get(0);
        assertThat(result.payslipCount()).isEqualTo(0);
        assertThat(result.totalGrossPay()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
