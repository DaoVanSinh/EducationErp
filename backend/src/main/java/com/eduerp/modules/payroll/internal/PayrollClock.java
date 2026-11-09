package com.eduerp.modules.payroll.internal;

import com.eduerp.modules.payroll.PayrollConstants;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** "Hôm nay" theo giờ trung tâm cho luật kỳ lương; test chốt được thời gian qua constructor nhận {@link Clock}. */
@Component
public class PayrollClock {

    private final Clock clock;

    @Autowired
    PayrollClock() {
        this(Clock.system(ZoneId.of(PayrollConstants.Schedules.CENTER_ZONE_ID)));
    }

    public PayrollClock(Clock clock) {
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }
}
