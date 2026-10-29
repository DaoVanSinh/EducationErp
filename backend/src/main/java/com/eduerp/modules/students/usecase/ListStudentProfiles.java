package com.eduerp.modules.students.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
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
    private final AccessManagement access;

    ListStudentProfiles(StudentProfileRepository profiles, IdentityManagement identity, AccessManagement access) {
        this.profiles = profiles;
        this.identity = identity;
        this.access = access;
    }

    /** Scope BRANCH chỉ thấy học viên có tài khoản thuộc chi nhánh của mình (spec 6.4, lớp 2). */
    @Transactional(readOnly = true)
    public PageResponse<StudentProfileResponse> execute(UUID actorAccountId, Pageable pageable) {
        var branchId = access.dataScopeOf(actorAccountId, AccessConstants.Resources.STUDENT,
                AccessConstants.Actions.READ).branchFilter(null);
        Page<StudentProfile> page = branchId == null ? profiles.findAll(pageable) : pageOfBranch(branchId, pageable);
        var accountIds = page.getContent().stream().map(StudentProfile::getAccountId).toList();
        var accountInfo = identity.summariesOf(accountIds);
        return PageResponse.of(page.map(profile -> toResponse(profile, accountInfo)));
    }

    /** Chi nhánh chưa có tài khoản nào: trả trang rỗng thay vì gửi {@code IN ()} rỗng xuống DB. */
    private Page<StudentProfile> pageOfBranch(UUID branchId, Pageable pageable) {
        var accountIds = identity.accountIdsByBranch(branchId);
        return accountIds.isEmpty() ? Page.empty(pageable) : profiles.findByAccountIdIn(accountIds, pageable);
    }

    private static StudentProfileResponse toResponse(StudentProfile profile,
            Map<UUID, IdentityManagement.AccountBasicInfo> accountInfo) {
        var account = accountInfo.get(profile.getAccountId());
        return new StudentProfileResponse(profile.getId(), profile.getAccountId(),
                account == null ? null : account.fullName(), account == null ? null : account.email(),
                profile.getDateOfBirth(), profile.getPhone(), profile.getSourceChannel(), profile.isActive());
    }
}
