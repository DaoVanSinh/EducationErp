package com.eduerp.modules.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.usecase.MarkOverdueInvoices;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

/** Job phải CỰC mỏng: mọi nghiệp vụ nằm trong MarkOverdueInvoices (spec mục 5). Test này chốt cả
 * hai điều đó - nó chỉ gọi usecase, và cron của nó lấy từ hằng số chứ không hardcode. */
class OverdueInvoiceSchedulerTest {

    private final MarkOverdueInvoices useCase = mock(MarkOverdueInvoices.class);
    private final OverdueInvoiceScheduler scheduler = new OverdueInvoiceScheduler(useCase);

    @Test
    void delegatesEverythingToTheUseCase() {
        when(useCase.execute()).thenReturn(3);

        scheduler.run();

        verify(useCase).execute();
    }

    @Test
    void runsOnTheCronDeclaredInBillingConstants() throws Exception {
        var annotation = OverdueInvoiceScheduler.class.getDeclaredMethod("run").getAnnotation(Scheduled.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.cron()).isEqualTo(BillingConstants.Schedules.MARK_OVERDUE_CRON);
    }
}
