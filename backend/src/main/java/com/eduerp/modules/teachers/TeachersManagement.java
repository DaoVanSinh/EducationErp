package com.eduerp.modules.teachers;

import com.eduerp.modules.teachers.internal.repository.TeacherProfileRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeachersManagement {

    public record ActiveTeacherSnapshot(UUID profileId, UUID accountId, List<String> subjects, String bio) {
    }

    private final TeacherProfileRepository profiles;

    TeachersManagement(TeacherProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public boolean teacherProfileExists(UUID accountId) {
        return profiles.existsByAccountId(accountId);
    }

    @Transactional(readOnly = true)
    public List<ActiveTeacherSnapshot> listActiveTeachers() {
        return profiles.findAllByActiveTrue().stream()
                .map(p -> new ActiveTeacherSnapshot(p.getId(), p.getAccountId(), p.getSubjects(), p.getBio()))
                .toList();
    }
}
