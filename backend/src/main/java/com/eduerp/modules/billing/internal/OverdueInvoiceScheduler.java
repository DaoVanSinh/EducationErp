package com.eduerp.modules.billing.internal;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.usecase.MarkOverdueInvoices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Thân job cố tình cực mỏng - chỉ gọi usecase, mọi nghiệp vụ nằm trong {@link MarkOverdueInvoices}
 * (spec mục 5). Cơ chế {@code @EnableScheduling} bật ở {@code core.config.SchedulingConfig}. */
@Component
class OverdueInvoiceScheduler {

    private static final Logger log = LoggerFactory.getLogger(OverdueInvoiceScheduler.class);

    private final MarkOverdueInvoices useCase;

    OverdueInvoiceScheduler(MarkOverdueInvoices useCase) {
        this.useCase = useCase;
    }

    @Scheduled(cron = BillingConstants.Schedules.MARK_OVERDUE_CRON)
    void run() {
        log.info("Đã đánh dấu quá hạn {} hoá đơn", useCase.execute());
    }
}
