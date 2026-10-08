package com.eduerp.modules.courses.dto;

import com.eduerp.modules.courses.CoursesConstants;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

public record WeeklyScheduleSlot(@NotNull CoursesConstants.DayOfWeek dayOfWeek,
        @NotNull LocalTime startTime, @NotNull LocalTime endTime) {
}
