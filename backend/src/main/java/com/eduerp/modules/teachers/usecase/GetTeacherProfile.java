package com.eduerp.modules.teachers.usecase;

import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.teachers.TeacherProfileNotFoundException;
import com.eduerp.modules.teachers.dto.TeacherProfileResponse;
import com.eduerp.modules.teachers.internal.model.TeacherProfile;
import com.eduerp.modules.teachers.internal.repository.TeacherProfileRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mirror {@code ListTeacherProfiles}, cho đúng một hồ sơ theo id - trang chi tiết giáo viên cần
 * fullName/email chứ không chỉ accountId. */
@Service
public class GetTeacherProfile {

    private final TeacherProfileRepository profiles;
    private final IdentityManagement identity;

    GetTeacherProfile(TeacherProfileRepository profiles, IdentityManagement identity) {
        this.profiles = profiles;
        this.identity = identity;
    }

    @Transactional(readOnly = true)
    public TeacherProfileResponse execute(UUID profileId) {
        var profile = profiles.findById(profileId).orElseThrow(() -> new TeacherProfileNotFoundException(profileId));
        var account = identity.summariesOf(List.of(profile.getAccountId())).get(profile.getAccountId());
        return toResponse(profile, account);
    }

    private static TeacherProfileResponse toResponse(TeacherProfile profile,
            IdentityManagement.AccountBasicInfo account) {
        return new TeacherProfileResponse(profile.getId(), profile.getAccountId(),
                account == null ? null : account.fullName(), account == null ? null : account.email(),
                profile.getSubjects(), profile.getPhone(), profile.getBio(), profile.isActive());
    }
}
