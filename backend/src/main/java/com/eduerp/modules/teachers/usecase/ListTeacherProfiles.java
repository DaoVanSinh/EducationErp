package com.eduerp.modules.teachers.usecase;

import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.teachers.dto.TeacherProfileResponse;
import com.eduerp.modules.teachers.internal.model.TeacherProfile;
import com.eduerp.modules.teachers.internal.repository.TeacherProfileRepository;
import com.eduerp.shared.PageResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListTeacherProfiles {

    private final TeacherProfileRepository profiles;
    private final IdentityManagement identity;

    ListTeacherProfiles(TeacherProfileRepository profiles, IdentityManagement identity) {
        this.profiles = profiles;
        this.identity = identity;
    }

    @Transactional(readOnly = true)
    public PageResponse<TeacherProfileResponse> execute(Pageable pageable) {
        Page<TeacherProfile> page = profiles.findAll(pageable);
        var accountIds = page.getContent().stream().map(TeacherProfile::getAccountId).toList();
        var accountInfo = identity.summariesOf(accountIds);
        return PageResponse.of(page.map(profile -> toResponse(profile, accountInfo)));
    }

    private static TeacherProfileResponse toResponse(TeacherProfile profile,
            Map<UUID, IdentityManagement.AccountBasicInfo> accountInfo) {
        var account = accountInfo.get(profile.getAccountId());
        return new TeacherProfileResponse(profile.getId(), profile.getAccountId(),
                account == null ? null : account.fullName(), account == null ? null : account.email(),
                profile.getSubjects(), profile.getPhone(), profile.getBio(), profile.isActive());
    }
}
