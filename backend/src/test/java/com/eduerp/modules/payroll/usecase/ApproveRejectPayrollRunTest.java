package com.eduerp.modules.payroll.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.PayrollEvents;
import com.eduerp.modules.payroll.PayrollRunNotPendingApprovalException;
import com.eduerp.modules.payroll.internal.model.PayrollRun;
import com.eduerp.modules.payroll.internal.repository.PayrollRunRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class ApproveRejectPayrollRunTest {

    private final PayrollRunRepository runs = mock(PayrollRunRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final ApprovePayrollRun approveUseCase = new ApprovePayrollRun(runs, events);
    private final RejectPayrollRun rejectUseCase = new RejectPayrollRun(runs);

    @Test
    void approveRejectsARunThatIsStillDraft() {
        var run = new PayrollRun(2026, 1);
        var runId = UUID.randomUUID();
        when(runs.findById(runId)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> approveUseCase.execute(runId, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(PayrollRunNotPendingApprovalException.class);
    }

    @Test
    void approveTransitionsToApprovedAndPublishesEvent() {
        var run = new PayrollRun(2026, 1);
        run.submitForApproval();
        var runId = UUID.randomUUID();
        when(runs.findById(runId)).thenReturn(Optional.of(run));

        approveUseCase.execute(runId, UUID.randomUUID(), UUID.randomUUID());

        assertThat(run.getStatus()).isEqualTo(PayrollConstants.PayrollRunStatus.APPROVED);
        verify(events).publishEvent(any(PayrollEvents.PayrollRunApproved.class));
    }

    @Test
    void rejectRejectsARunThatIsStillDraft() {
        var run = new PayrollRun(2026, 1);
        var runId = UUID.randomUUID();
        when(runs.findById(runId)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> rejectUseCase.execute(runId, "Thiếu giờ CTV", UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(PayrollRunNotPendingApprovalException.class);
    }

    @Test
    void rejectTransitionsBackToDraftWithReason() {
        var run = new PayrollRun(2026, 1);
        run.submitForApproval();
        var runId = UUID.randomUUID();
        when(runs.findById(runId)).thenReturn(Optional.of(run));

        rejectUseCase.execute(runId, "Sai số giờ dạy", UUID.randomUUID(), UUID.randomUUID());

        assertThat(run.getStatus()).isEqualTo(PayrollConstants.PayrollRunStatus.DRAFT);
        assertThat(run.getRejectionReason()).isEqualTo("Sai số giờ dạy");
    }
}
