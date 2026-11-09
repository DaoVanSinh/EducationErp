package com.eduerp.modules.attendance.internal.repository;

import java.time.LocalDate;
import java.util.UUID;

/** Kết quả gộp theo lớp của {@link AttendanceSessionRepository#summarizeTaughtClasses}. */
public interface TaughtClassRow {

    UUID getClassId();

    long getSessionCount();

    long getTotalMinutes();

    LocalDate getFirstSessionDate();

    LocalDate getLastSessionDate();
}
