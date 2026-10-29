package com.eduerp.modules.courses.internal;

import com.eduerp.modules.courses.CoursesConstants;
import com.eduerp.modules.courses.usecase.AutoStartClasses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Thân job cố tình cực mỏng - mirror {@code OverdueInvoiceScheduler}; nghiệp vụ nằm trong {@link AutoStartClasses}. */
@Component
class ClassAutoStartScheduler {

    private static final Logger log = LoggerFactory.getLogger(ClassAutoStartScheduler.class);

    private final AutoStartClasses useCase;

    ClassAutoStartScheduler(AutoStartClasses useCase) {
        this.useCase = useCase;
    }

    @Scheduled(cron = CoursesConstants.Schedules.AUTO_START_CRON, zone = CoursesConstants.Schedules.CENTER_ZONE_ID)
    void run() {
        log.info("Đã tự khai giảng {} lớp", useCase.execute());
    }
}
