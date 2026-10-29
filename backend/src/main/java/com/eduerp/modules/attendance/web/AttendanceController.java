package com.eduerp.modules.attendance.web;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.attendance.dto.ClassAttendanceResponse;
import com.eduerp.modules.attendance.dto.RecordAttendanceRequest;
import com.eduerp.modules.attendance.usecase.GetClassAttendance;
import com.eduerp.modules.attendance.usecase.RecordAttendance;
import com.eduerp.shared.AccountPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Điểm danh theo lớp. {@code @PreAuthorize} không đòi scope ORGANIZATION vì giáo viên chỉ có scope
 * PERSONAL - kiểm "lớp này có phải lớp của anh không" nằm trong usecase qua guard.
 */
@RestController
@RequestMapping("/api/attendance")
class AttendanceController {

    private final RecordAttendance recordAttendance;
    private final GetClassAttendance getClassAttendance;

    AttendanceController(RecordAttendance recordAttendance, GetClassAttendance getClassAttendance) {
        this.recordAttendance = recordAttendance;
        this.getClassAttendance = getClassAttendance;
    }

    /** POST upsert theo (classId, sessionDate) - gửi lại cùng ngày là sửa buổi đó. */
    @PostMapping("/sessions")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_ATTENDANCE)
    void record(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody RecordAttendanceRequest request) {
        recordAttendance.execute(principal.accountId(), principal.homeBranchId(), request);
    }

    @GetMapping("/classes/{classId}")
    @PreAuthorize(AccessConstants.AccessRules.READ_ATTENDANCE)
    ClassAttendanceResponse classAttendance(@AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID classId) {
        return getClassAttendance.execute(principal.accountId(), classId);
    }
}
