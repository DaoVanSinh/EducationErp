package com.eduerp.modules.attendance.web;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.attendance.dto.MyAttendanceClassResponse;
import com.eduerp.modules.attendance.usecase.GetMyAttendance;
import com.eduerp.shared.AccountPrincipal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Cổng học tập: điểm danh của CHÍNH học viên - luôn lọc theo {@code principal.accountId()}. */
@RestController
@RequestMapping("/api/attendance/me")
class AttendanceSelfServiceController {

    private final GetMyAttendance getMyAttendance;

    AttendanceSelfServiceController(GetMyAttendance getMyAttendance) {
        this.getMyAttendance = getMyAttendance;
    }

    @GetMapping("/classes")
    @PreAuthorize(AccessConstants.AccessRules.READ_LEARNING)
    List<MyAttendanceClassResponse> myAttendance(@AuthenticationPrincipal AccountPrincipal principal) {
        return getMyAttendance.execute(principal.accountId());
    }
}
