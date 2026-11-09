package com.eduerp.modules.courses.internal;

import com.eduerp.modules.courses.ClassLifecycleException;
import com.eduerp.modules.courses.CoursesConstants;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.identity.AccountLifecycleParticipant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Còn lớp đang mở thì chưa đóng tài khoản và chưa chuyển chi nhánh được: phải bàn giao lớp cho giáo viên khác trước. */
@Component
class TeacherClassesLifecycle implements AccountLifecycleParticipant {

    private static final List<CoursesConstants.ClassStatus> OPEN_STATUSES = Arrays.stream(
            CoursesConstants.ClassStatus.values()).filter(CoursesConstants.ClassStatus::isOpen).toList();

    private final ClassRepository classes;

    TeacherClassesLifecycle(ClassRepository classes) {
        this.classes = classes;
    }

    @Override
    public void beforeClose(UUID accountId) {
        requireNoOpenClasses(accountId);
    }

    @Override
    public void beforeBranchTransfer(UUID accountId, UUID newBranchId) {
        requireNoOpenClasses(accountId);
    }

    private void requireNoOpenClasses(UUID accountId) {
        var openClasses = classes.countByTeacherIdAndStatusIn(accountId, OPEN_STATUSES);
        if (openClasses > 0) {
            throw new ClassLifecycleException.TeacherHasOpenClasses(accountId, openClasses);
        }
    }
}
