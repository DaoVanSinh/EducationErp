package com.eduerp.modules.attendance.dto;

import com.eduerp.modules.attendance.AttendanceConstants;
import java.time.LocalDate;

/** Trạng thái của học viên đang đăng nhập ở một buổi. */
public record MyAttendanceItem(LocalDate sessionDate, AttendanceConstants.AttendanceStatus status, String note) {
}
