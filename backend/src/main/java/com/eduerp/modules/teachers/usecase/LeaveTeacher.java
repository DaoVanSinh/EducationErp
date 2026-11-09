package com.eduerp.modules.teachers.usecase;

import com.eduerp.core.exception.AppValidationException;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.courses.ClassLifecycleException;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.teachers.TeacherProfileNotFoundException;
import com.eduerp.modules.teachers.TeachersConstants;
import com.eduerp.modules.teachers.TeachersEvents;
import com.eduerp.modules.teachers.dto.LeaveTeacherRequest;
import com.eduerp.modules.teachers.internal.access.TeacherBranchGuard;
import com.eduerp.modules.teachers.internal.repository.TeacherProfileRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cho nghỉ: lương tháng dừng từ ngày nghỉ, giáo viên không nhận lớp mới được. Tài khoản vẫn đăng nhập để xem lớp đã dạy
 * và phiếu lương cho tới khi admin đóng. Còn lớp đang mở thì phải bàn giao trước. Quay lại làm là tạo tài khoản mới.
 */
@Service
public class LeaveTeacher {

    private final TeacherProfileRepository profiles;
    private final TeacherBranchGuard branchGuard;
    private final CoursesManagement courses;
    private final ApplicationEventPublisher events;

    LeaveTeacher(TeacherProfileRepository profiles, TeacherBranchGuard branchGuard, CoursesManagement courses,
            ApplicationEventPublisher events) {
        this.profiles = profiles;
        this.branchGuard = branchGuard;
        this.courses = courses;
        this.events = events;
    }

    @Transactional
    public void execute(UUID profileId, UUID actorAccountId, UUID actorBranchId, LeaveTeacherRequest request) {
        var profile = profiles.findById(profileId).orElseThrow(() -> new TeacherProfileNotFoundException(profileId));
        branchGuard.require(actorAccountId, profile, AccessConstants.Actions.UPDATE);
        if (!profile.isActive()) {
            return;
        }
        var openClasses = courses.openClassCountOf(profile.getAccountId());
        if (openClasses > 0) {
            throw new ClassLifecycleException.TeacherHasOpenClasses(profile.getAccountId(), openClasses);
        }
        var today = LocalDate.now(ZoneId.of(TeachersConstants.Schedules.CENTER_ZONE_ID));
        if (request.lastWorkDate().isAfter(today)) {
            throw new AppValidationException("lastWorkDate", "Ngày nghỉ không được ở tương lai");
        }
        if (profile.getWorkStartDate() != null && request.lastWorkDate().isBefore(profile.getWorkStartDate())) {
            throw new AppValidationException("lastWorkDate", "Ngày nghỉ phải sau ngày bắt đầu làm việc");
        }
        profile.leave(request.lastWorkDate());
        events.publishEvent(new TeachersEvents.TeacherLeft(profileId, actorAccountId, actorBranchId));
    }
}
