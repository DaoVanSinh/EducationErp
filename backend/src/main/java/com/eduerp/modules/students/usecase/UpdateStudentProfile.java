package com.eduerp.modules.students.usecase;

import com.eduerp.modules.students.StudentProfileNotFoundException;
import com.eduerp.modules.students.StudentsEvents;
import com.eduerp.modules.students.dto.UpdateStudentProfileRequest;
import com.eduerp.modules.students.internal.repository.StudentProfileRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateStudentProfile {

    private final StudentProfileRepository profiles;
    private final ApplicationEventPublisher events;

    UpdateStudentProfile(StudentProfileRepository profiles, ApplicationEventPublisher events) {
        this.profiles = profiles;
        this.events = events;
    }

    @Transactional
    public void execute(UUID profileId, UUID actorAccountId, UUID actorBranchId,
            UpdateStudentProfileRequest request) {
        var profile = profiles.findById(profileId).orElseThrow(() -> new StudentProfileNotFoundException(profileId));
        profile.setDateOfBirth(request.dateOfBirth());
        profile.setPhone(request.phone());
        profile.setSourceChannel(request.sourceChannel());
        profile.setActive(request.active());
        events.publishEvent(new StudentsEvents.StudentProfileUpdated(profileId, actorAccountId, actorBranchId));
    }
}
