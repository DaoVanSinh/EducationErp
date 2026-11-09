package com.eduerp.modules.teachers.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.teachers.TeacherProfileNotFoundException;
import com.eduerp.modules.teachers.TeachersEvents;
import com.eduerp.modules.teachers.dto.UpdateTeacherProfileRequest;
import com.eduerp.modules.teachers.internal.access.TeacherBranchGuard;
import com.eduerp.modules.teachers.internal.repository.TeacherProfileRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sửa thông tin hồ sơ. Cho nghỉ là thao tác riêng ({@link LeaveTeacher}) vì nó có ngày nghỉ và dừng lương. */
@Service
public class UpdateTeacherProfile {

    private final TeacherProfileRepository profiles;
    private final TeacherBranchGuard branchGuard;
    private final ApplicationEventPublisher events;

    UpdateTeacherProfile(TeacherProfileRepository profiles, TeacherBranchGuard branchGuard,
            ApplicationEventPublisher events) {
        this.profiles = profiles;
        this.branchGuard = branchGuard;
        this.events = events;
    }

    @Transactional
    public void execute(UUID profileId, UUID actorAccountId, UUID actorBranchId,
            UpdateTeacherProfileRequest request) {
        var profile = profiles.findById(profileId).orElseThrow(() -> new TeacherProfileNotFoundException(profileId));
        branchGuard.require(actorAccountId, profile, AccessConstants.Actions.UPDATE);
        profile.setSubjects(request.subjects());
        profile.setPhone(request.phone());
        profile.setBio(request.bio());
        events.publishEvent(new TeachersEvents.TeacherProfileUpdated(profileId, actorAccountId, actorBranchId));
    }
}
