package com.eduerp.modules.teachers.usecase;

import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.teachers.dto.TeacherProfileResponse;
import com.eduerp.modules.teachers.internal.model.TeacherProfile;
import com.eduerp.modules.teachers.internal.repository.TeacherProfileRepository;
import com.eduerp.shared.PageResponse;
import java.util.HashSet;
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
    private final CoursesManagement courses;

    ListTeacherProfiles(TeacherProfileRepository profiles, IdentityManagement identity, CoursesManagement courses) {
        this.profiles = profiles;
        this.identity = identity;
        this.courses = courses;
    }

    @Transactional(readOnly = true)
    public PageResponse<TeacherProfileResponse> execute(Pageable pageable, UUID branchId) {
        Page<TeacherProfile> page = branchId == null ? profiles.findAll(pageable) : pageOfBranch(branchId, pageable);
        var accountIds = page.getContent().stream().map(TeacherProfile::getAccountId).toList();
        var accountInfo = identity.summariesOf(accountIds);
        return PageResponse.of(page.map(profile -> toResponse(profile, accountInfo)));
    }

    /** Giáo viên của một chi nhánh: cơ sở chính ở đó, hoặc đang dạy lớp còn mở ở đó (nhiều người dạy nhiều cơ sở). */
    private Page<TeacherProfile> pageOfBranch(UUID branchId, Pageable pageable) {
        var accountIds = new HashSet<>(identity.accountIdsByBranch(branchId));
        accountIds.addAll(courses.teacherIdsTeachingAt(branchId));
        return accountIds.isEmpty() ? Page.empty(pageable) : profiles.findByAccountIdIn(accountIds, pageable);
    }

    private static TeacherProfileResponse toResponse(TeacherProfile profile,
            Map<UUID, IdentityManagement.AccountBasicInfo> accountInfo) {
        var account = accountInfo.get(profile.getAccountId());
        return new TeacherProfileResponse(profile.getId(), profile.getAccountId(),
                account == null ? null : account.fullName(), account == null ? null : account.email(),
                profile.getSubjects(), profile.getPhone(), profile.getBio(), profile.isActive());
    }
}
