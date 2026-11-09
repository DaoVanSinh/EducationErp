package com.eduerp.modules.teachers.internal;

import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.organization.OrganizationManagement;
import com.eduerp.modules.teachers.dto.TeacherProfileResponse;
import com.eduerp.modules.teachers.internal.model.TeacherProfile;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/** Ghép tên, email, chi nhánh theo lô cho danh sách và chi tiết hồ sơ giáo viên - không N+1 theo số dòng. */
@Component
public class TeacherProfileAssembler {

    private final IdentityManagement identity;
    private final OrganizationManagement organization;

    TeacherProfileAssembler(IdentityManagement identity, OrganizationManagement organization) {
        this.identity = identity;
        this.organization = organization;
    }

    public TeacherProfileResponse toResponse(TeacherProfile profile) {
        return toResponses(List.of(profile)).get(0);
    }

    public Page<TeacherProfileResponse> toPage(Page<TeacherProfile> page) {
        var responses = toResponses(page.getContent()).iterator();
        return page.map(profile -> responses.next());
    }

    private List<TeacherProfileResponse> toResponses(List<TeacherProfile> profiles) {
        var accountIds = profiles.stream().map(TeacherProfile::getAccountId).toList();
        var accounts = identity.summariesOf(accountIds);
        var homeBranches = identity.homeBranchesOf(accountIds);
        var branchNames = organization.namesOf(homeBranches.values());
        return profiles.stream().map(profile -> {
            var account = accounts.get(profile.getAccountId());
            UUID branchId = homeBranches.get(profile.getAccountId());
            return new TeacherProfileResponse(profile.getId(), profile.getAccountId(),
                    account == null ? null : account.fullName(), account == null ? null : account.email(),
                    profile.getSubjects(), profile.getPhone(), profile.getBio(), profile.isActive(),
                    profile.isPayConfigured(), branchId, branchId == null ? null : branchNames.get(branchId),
                    profile.getDeactivatedOn());
        }).toList();
    }
}
