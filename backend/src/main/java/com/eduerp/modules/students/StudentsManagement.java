package com.eduerp.modules.students;

import com.eduerp.modules.students.internal.repository.StudentProfileRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentsManagement {

    private final StudentProfileRepository profiles;

    StudentsManagement(StudentProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public boolean studentProfileExists(UUID accountId) {
        return profiles.existsByAccountId(accountId);
    }
}
