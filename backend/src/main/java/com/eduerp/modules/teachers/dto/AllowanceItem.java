package com.eduerp.modules.teachers.dto;

import com.eduerp.modules.teachers.TeachersConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Một khoản phụ cấp cố định hằng tháng. */
public record AllowanceItem(@NotBlank @Size(max = TeachersConstants.Limits.MAX_ALLOWANCE_NAME_LENGTH) String name,
        @NotNull @Positive BigDecimal amount) {
}
