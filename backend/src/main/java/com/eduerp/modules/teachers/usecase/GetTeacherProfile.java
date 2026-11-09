package com.eduerp.modules.teachers.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.teachers.TeacherProfileNotFoundException;
import com.eduerp.modules.teachers.dto.TeacherProfileResponse;
import com.eduerp.modules.teachers.internal.TeacherProfileAssembler;
import com.eduerp.modules.teachers.internal.access.TeacherBranchGuard;
import com.eduerp.modules.teachers.internal.repository.TeacherProfileRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Một hồ sơ theo id. Admin chi nhánh chỉ xem được giáo viên thuộc chi nhánh mình. */
@Service
public class GetTeacherProfile {

    private final TeacherProfileRepository profiles;
    private final TeacherBranchGuard branchGuard;
    private final TeacherProfileAssembler assembler;

    GetTeacherProfile(TeacherProfileRepository profiles, TeacherBranchGuard branchGuard,
            TeacherProfileAssembler assembler) {
        this.profiles = profiles;
        this.branchGuard = branchGuard;
        this.assembler = assembler;
    }

    @Transactional(readOnly = true)
    public TeacherProfileResponse execute(UUID actorAccountId, UUID profileId) {
        var profile = profiles.findById(profileId).orElseThrow(() -> new TeacherProfileNotFoundException(profileId));
        branchGuard.require(actorAccountId, profile, AccessConstants.Actions.READ);
        return assembler.toResponse(profile);
    }
}
