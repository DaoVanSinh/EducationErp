package com.eduerp.modules.teachers.usecase;

import com.eduerp.modules.teachers.TeacherProfileNotFoundException;
import com.eduerp.modules.teachers.dto.TeacherPayResponse;
import com.eduerp.modules.teachers.internal.repository.TeacherProfileRepository;
import com.eduerp.modules.teachers.internal.util.TeacherPayMapper;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Xem điều khoản lương - gác bằng READ_PAYROLL ở controller, không phải READ_TEACHER. */
@Service
public class GetTeacherPay {

    private final TeacherProfileRepository profiles;

    GetTeacherPay(TeacherProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public TeacherPayResponse execute(UUID profileId) {
        var profile = profiles.findById(profileId).orElseThrow(() -> new TeacherProfileNotFoundException(profileId));
        return TeacherPayMapper.toResponse(profile);
    }
}
