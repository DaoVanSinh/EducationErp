package com.eduerp.modules.teachers.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.teachers.dto.TeacherProfileResponse;
import com.eduerp.modules.teachers.internal.TeacherProfileAssembler;
import com.eduerp.modules.teachers.internal.access.TeacherBranchGuard;
import com.eduerp.modules.teachers.internal.model.TeacherProfile;
import com.eduerp.modules.teachers.internal.repository.TeacherProfileRepository;
import com.eduerp.shared.PageResponse;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListTeacherProfiles {

    private final TeacherProfileRepository profiles;
    private final IdentityManagement identity;
    private final CoursesManagement courses;
    private final TeacherBranchGuard branchGuard;
    private final TeacherProfileAssembler assembler;

    ListTeacherProfiles(TeacherProfileRepository profiles, IdentityManagement identity, CoursesManagement courses,
            TeacherBranchGuard branchGuard, TeacherProfileAssembler assembler) {
        this.profiles = profiles;
        this.identity = identity;
        this.courses = courses;
        this.branchGuard = branchGuard;
        this.assembler = assembler;
    }

    /** Scope BRANCH bị ép về chi nhánh của người gọi; {@code query} tìm theo tên hoặc email. */
    @Transactional(readOnly = true)
    public PageResponse<TeacherProfileResponse> execute(UUID actorAccountId, Pageable pageable,
            UUID requestedBranchId, String query) {
        var branchId = branchGuard.scopeOf(actorAccountId, AccessConstants.Actions.READ)
                .branchFilter(requestedBranchId);
        Set<UUID> accountIds = branchId == null ? null : accountsOfBranch(branchId);
        if (query != null && !query.isBlank()) {
            var matching = new HashSet<>(identity.accountIdsMatching(query));
            if (accountIds != null) {
                matching.retainAll(accountIds);
            }
            accountIds = matching;
        }
        if (accountIds == null) {
            return PageResponse.of(assembler.toPage(profiles.findAll(pageable)));
        }
        Page<TeacherProfile> page = accountIds.isEmpty() ? Page.empty(pageable)
                : profiles.findByAccountIdIn(accountIds, pageable);
        return PageResponse.of(assembler.toPage(page));
    }

    /** Giáo viên của một chi nhánh: cơ sở chính ở đó, hoặc đang dạy lớp còn mở ở đó (lớp cũ giữ tới khi kết thúc). */
    private Set<UUID> accountsOfBranch(UUID branchId) {
        var accountIds = new HashSet<>(identity.accountIdsByBranch(branchId));
        accountIds.addAll(courses.teacherIdsTeachingAt(branchId));
        return accountIds;
    }
}
