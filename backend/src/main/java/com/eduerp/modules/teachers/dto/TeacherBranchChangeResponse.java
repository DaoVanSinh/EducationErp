package com.eduerp.modules.teachers.dto;

import java.time.Instant;

/** Một lần chuyển chi nhánh chủ quản. {@code fromBranchName} null khi trước đó chưa thuộc chi nhánh nào. */
public record TeacherBranchChangeResponse(String fromBranchName, String toBranchName, Instant changedAt) {
}
