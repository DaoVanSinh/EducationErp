package com.eduerp.modules.attendance.internal.repository;

import java.util.UUID;

/** Kết quả gộp theo giáo viên của {@link AttendanceSessionRepository#summarizeTeaching}. */
public interface TeachingSummaryRow {

    UUID getTeacherAccountId();

    long getSessionCount();

    long getTotalMinutes();
}
