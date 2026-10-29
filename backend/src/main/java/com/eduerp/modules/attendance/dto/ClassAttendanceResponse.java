package com.eduerp.modules.attendance.dto;

import java.util.List;
import java.util.UUID;

/** Bảng điểm danh của một lớp: các buổi theo ngày tăng dần và mỗi học viên một dòng. */
public record ClassAttendanceResponse(UUID classId, List<AttendanceSessionResponse> sessions,
        List<StudentAttendanceRow> rows) {
}
