package com.eduerp.modules.teachers.internal.access;

import com.eduerp.modules.identity.AccountLifecycleParticipant;
import com.eduerp.modules.teachers.internal.repository.TeacherProfileRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Đóng tài khoản là dừng lương từ hôm đó, nếu giáo viên chưa được Cho nghỉ từ trước. */
@Component
class TeacherAccountLifecycle implements AccountLifecycleParticipant {

    private final TeacherProfileRepository profiles;

    TeacherAccountLifecycle(TeacherProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Override
    public void afterClose(UUID accountId, LocalDate closedOn) {
        profiles.findByAccountId(accountId).filter(profile -> profile.isActive())
                .ifPresent(profile -> profile.leave(closedOn));
    }
}
