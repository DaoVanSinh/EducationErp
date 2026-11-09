package com.eduerp.modules.teachers.internal.access;

import com.eduerp.modules.courses.TeacherEligibilityPort;
import com.eduerp.modules.teachers.internal.repository.TeacherProfileRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Chỉ giáo viên còn làm việc và đã có lương mới nhận lớp: dạy mà chưa có lương sẽ chặn cả kỳ lương của công ty. */
@Component
class TeacherAssignmentEligibility implements TeacherEligibilityPort {

    private final TeacherProfileRepository profiles;

    TeacherAssignmentEligibility(TeacherProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Override
    public Optional<String> assignmentProblem(UUID teacherAccountId) {
        var profile = profiles.findByAccountId(teacherAccountId);
        if (profile.isEmpty()) {
            return Optional.of("Tài khoản này chưa có hồ sơ giáo viên");
        }
        if (!profile.get().isActive()) {
            return Optional.of("Giáo viên này đã nghỉ");
        }
        if (!profile.get().isPayConfigured()) {
            return Optional.of("Giáo viên này chưa thiết lập lương");
        }
        return Optional.empty();
    }
}
