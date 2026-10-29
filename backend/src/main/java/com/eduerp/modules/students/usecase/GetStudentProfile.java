package com.eduerp.modules.students.usecase;

import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.students.StudentProfileNotFoundException;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.students.dto.StudentProfileResponse;
import com.eduerp.modules.students.internal.access.StudentBranchGuard;
import com.eduerp.modules.students.internal.model.StudentProfile;
import com.eduerp.modules.students.internal.repository.StudentProfileRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mirror {@code ListStudentProfiles}, cho đúng một hồ sơ theo id - trang chi tiết học viên cần
 * fullName/email chứ không chỉ accountId. */
@Service
public class GetStudentProfile {

    private final StudentProfileRepository profiles;
    private final IdentityManagement identity;
    private final StudentBranchGuard branchGuard;

    GetStudentProfile(StudentProfileRepository profiles, IdentityManagement identity, StudentBranchGuard branchGuard) {
        this.profiles = profiles;
        this.identity = identity;
        this.branchGuard = branchGuard;
    }

    @Transactional(readOnly = true)
    public StudentProfileResponse execute(UUID actorAccountId, UUID profileId) {
        var profile = profiles.findById(profileId).orElseThrow(() -> new StudentProfileNotFoundException(profileId));
        branchGuard.require(actorAccountId, profile, AccessConstants.Actions.READ);
        var account = identity.summariesOf(List.of(profile.getAccountId())).get(profile.getAccountId());
        return toResponse(profile, account);
    }

    private static StudentProfileResponse toResponse(StudentProfile profile,
            IdentityManagement.AccountBasicInfo account) {
        return new StudentProfileResponse(profile.getId(), profile.getAccountId(),
                account == null ? null : account.fullName(), account == null ? null : account.email(),
                profile.getDateOfBirth(), profile.getPhone(), profile.getSourceChannel(), profile.isActive());
    }
}
