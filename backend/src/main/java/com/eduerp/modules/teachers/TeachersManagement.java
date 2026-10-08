package com.eduerp.modules.teachers;

import com.eduerp.modules.teachers.internal.repository.TeacherProfileRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeachersManagement {

    private final TeacherProfileRepository profiles;

    TeachersManagement(TeacherProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public boolean teacherProfileExists(UUID accountId) {
        return profiles.existsByAccountId(accountId);
    }
}
