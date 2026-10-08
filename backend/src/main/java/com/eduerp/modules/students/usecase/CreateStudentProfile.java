package com.eduerp.modules.students.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.students.StudentAccountRoleMismatchException;
import com.eduerp.modules.students.StudentProfileAlreadyExistsException;
import com.eduerp.modules.students.StudentsEvents;
import com.eduerp.modules.students.dto.CreateStudentProfileRequest;
import com.eduerp.modules.students.internal.model.StudentProfile;
import com.eduerp.modules.students.internal.repository.StudentProfileRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateStudentProfile {

    private final StudentProfileRepository profiles;
    private final AccessManagement access;
    private final ApplicationEventPublisher events;

    CreateStudentProfile(StudentProfileRepository profiles, AccessManagement access,
            ApplicationEventPublisher events) {
        this.profiles = profiles;
        this.access = access;
        this.events = events;
    }

    @Transactional
    public UUID execute(UUID actorAccountId, UUID actorBranchId, CreateStudentProfileRequest request) {
        var role = access.roleOf(request.accountId()).orElse(null);
        if (role == null || !AccessConstants.RoleCodes.STUDENT.equals(role.code())) {
            throw new StudentAccountRoleMismatchException(request.accountId());
        }
        if (profiles.existsByAccountId(request.accountId())) {
            throw new StudentProfileAlreadyExistsException(request.accountId());
        }

        var saved = profiles.save(new StudentProfile(request.accountId(), request.dateOfBirth(), request.phone(),
                request.sourceChannel()));
        events.publishEvent(new StudentsEvents.StudentProfileCreated(saved.getId(), actorAccountId, actorBranchId));
        return saved.getId();
    }
}
