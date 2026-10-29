package com.eduerp.modules.courses.internal;

import com.eduerp.modules.courses.CoursesConstants;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * "Hôm nay" theo múi giờ trung tâm cho mọi quyết định theo ngày của module (ngày khai giảng, tự khai giảng).
 * Một chỗ duy nhất để không có use case nào lỡ dùng múi giờ của máy chủ; test chốt được thời gian qua
 * constructor nhận {@link Clock}.
 */
@Component
public class CenterClock {

    private final Clock clock;

    @Autowired
    CenterClock() {
        this(Clock.system(ZoneId.of(CoursesConstants.Schedules.CENTER_ZONE_ID)));
    }

    public CenterClock(Clock clock) {
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public Instant now() {
        return clock.instant();
    }
}
