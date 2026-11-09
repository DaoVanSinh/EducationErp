package com.eduerp.modules.students.internal.access;

import com.eduerp.modules.identity.AccountLifecycleParticipant;
import com.eduerp.modules.students.internal.repository.StudentProfileRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Đóng tài khoản thì hồ sơ ngừng hoạt động, lịch sử học giữ nguyên. */
@Component
class StudentAccountLifecycle implements AccountLifecycleParticipant {

    private final StudentProfileRepository profiles;

    StudentAccountLifecycle(StudentProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Override
    public void afterClose(UUID accountId, LocalDate closedOn) {
        profiles.findByAccountId(accountId).ifPresent(profile -> profile.setActive(false));
    }
}
