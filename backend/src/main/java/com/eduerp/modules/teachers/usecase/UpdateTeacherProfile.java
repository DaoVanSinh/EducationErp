package com.eduerp.modules.teachers.usecase;

import com.eduerp.modules.teachers.TeacherProfileNotFoundException;
import com.eduerp.modules.teachers.TeachersEvents;
import com.eduerp.modules.teachers.dto.UpdateTeacherProfileRequest;
import com.eduerp.modules.teachers.internal.repository.TeacherProfileRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateTeacherProfile {

    private final TeacherProfileRepository profiles;
    private final ApplicationEventPublisher events;

    UpdateTeacherProfile(TeacherProfileRepository profiles, ApplicationEventPublisher events) {
        this.profiles = profiles;
        this.events = events;
    }

    @Transactional
    public void execute(UUID profileId, UUID actorAccountId, UUID actorBranchId,
            UpdateTeacherProfileRequest request) {
        var profile = profiles.findById(profileId).orElseThrow(() -> new TeacherProfileNotFoundException(profileId));
        profile.setSubjects(request.subjects());
        profile.setPhone(request.phone());
        profile.setBio(request.bio());
        profile.setActive(request.active());
        events.publishEvent(new TeachersEvents.TeacherProfileUpdated(profileId, actorAccountId, actorBranchId));
    }
}
