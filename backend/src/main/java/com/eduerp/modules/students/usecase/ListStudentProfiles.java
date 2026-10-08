package com.eduerp.modules.students.usecase;

import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.students.dto.StudentProfileResponse;
import com.eduerp.modules.students.internal.model.StudentProfile;
import com.eduerp.modules.students.internal.repository.StudentProfileRepository;
import com.eduerp.shared.PageResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListStudentProfiles {

    private final StudentProfileRepository profiles;
    private final IdentityManagement identity;

    ListStudentProfiles(StudentProfileRepository profiles, IdentityManagement identity) {
        this.profiles = profiles;
        this.identity = identity;
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentProfileResponse> execute(Pageable pageable) {
        Page<StudentProfile> page = profiles.findAll(pageable);
        var accountIds = page.getContent().stream().map(StudentProfile::getAccountId).toList();
        var accountInfo = identity.summariesOf(accountIds);
        return PageResponse.of(page.map(profile -> toResponse(profile, accountInfo)));
    }

    private static StudentProfileResponse toResponse(StudentProfile profile,
            Map<UUID, IdentityManagement.AccountBasicInfo> accountInfo) {
        var account = accountInfo.get(profile.getAccountId());
        return new StudentProfileResponse(profile.getId(), profile.getAccountId(),
                account == null ? null : account.fullName(), account == null ? null : account.email(),
                profile.getDateOfBirth(), profile.getPhone(), profile.getSourceChannel(), profile.isActive());
    }
}
