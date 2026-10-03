package com.eduerp.modules.teachers.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.teachers.TeacherAccountRoleMismatchException;
import com.eduerp.modules.teachers.TeacherProfileAlreadyExistsException;
import com.eduerp.modules.teachers.TeachersEvents;
import com.eduerp.modules.teachers.dto.CreateTeacherProfileRequest;
import com.eduerp.modules.teachers.internal.model.TeacherProfile;
import com.eduerp.modules.teachers.internal.repository.TeacherProfileRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateTeacherProfile {

    private final TeacherProfileRepository profiles;
    private final AccessManagement access;
    private final ApplicationEventPublisher events;

    CreateTeacherProfile(TeacherProfileRepository profiles, AccessManagement access,
            ApplicationEventPublisher events) {
        this.profiles = profiles;
        this.access = access;
        this.events = events;
    }

    @Transactional
    public UUID execute(UUID actorAccountId, UUID actorBranchId, CreateTeacherProfileRequest request) {
        var role = access.roleOf(request.accountId()).orElse(null);
        if (role == null || !AccessConstants.RoleCodes.TEACHER.equals(role.code())) {
            throw new TeacherAccountRoleMismatchException(request.accountId());
        }
        if (profiles.existsByAccountId(request.accountId())) {
            throw new TeacherProfileAlreadyExistsException(request.accountId());
        }

        var saved = profiles.save(new TeacherProfile(request.accountId(), request.subjects(), request.phone(),
                request.bio()));
        events.publishEvent(new TeachersEvents.TeacherProfileCreated(saved.getId(), actorAccountId, actorBranchId));
        return saved.getId();
    }
}
