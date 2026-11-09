package com.eduerp.modules.teachers.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record LeaveTeacherRequest(@NotNull LocalDate lastWorkDate) {
}
