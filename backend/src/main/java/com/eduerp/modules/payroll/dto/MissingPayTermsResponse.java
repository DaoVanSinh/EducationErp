package com.eduerp.modules.payroll.dto;

import java.util.UUID;

/** Giáo viên chưa thiết lập lương. {@code sessionCount} lớn hơn 0 thì kỳ lương chưa gửi duyệt được. */
public record MissingPayTermsResponse(UUID accountId, String fullName, int sessionCount) {
}
