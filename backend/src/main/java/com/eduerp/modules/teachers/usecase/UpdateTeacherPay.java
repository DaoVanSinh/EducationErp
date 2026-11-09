package com.eduerp.modules.teachers.usecase;

import com.eduerp.modules.teachers.TeacherProfileNotFoundException;
import com.eduerp.modules.teachers.TeachersEvents;
import com.eduerp.modules.teachers.dto.TeacherPayResponse;
import com.eduerp.modules.teachers.dto.TeacherPayTermsRequest;
import com.eduerp.modules.teachers.internal.repository.TeacherProfileRepository;
import com.eduerp.modules.teachers.internal.rules.TeacherPayRules;
import com.eduerp.modules.teachers.internal.util.TeacherPayMapper;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đổi điều khoản lương. Kỳ đã duyệt không bị ảnh hưởng vì phiếu lương là ảnh chụp; kỳ còn nháp nhận mức mới khi
 * tính lại. Đây cũng là đường thiết lập lương cho hồ sơ sinh tự động khi được cấp vai trò giảng dạy.
 */
@Service
public class UpdateTeacherPay {

    private final TeacherProfileRepository profiles;
    private final ApplicationEventPublisher events;

    UpdateTeacherPay(TeacherProfileRepository profiles, ApplicationEventPublisher events) {
        this.profiles = profiles;
        this.events = events;
    }

    @Transactional
    public TeacherPayResponse execute(UUID profileId, UUID actorAccountId, UUID actorBranchId,
            TeacherPayTermsRequest request) {
        TeacherPayRules.requireValid(request);
        var profile = profiles.findById(profileId).orElseThrow(() -> new TeacherProfileNotFoundException(profileId));
        TeacherPayMapper.apply(profile, request);
        events.publishEvent(new TeachersEvents.TeacherPayUpdated(profileId, actorAccountId, actorBranchId));
        return TeacherPayMapper.toResponse(profile);
    }
}
