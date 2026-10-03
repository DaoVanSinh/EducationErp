package com.eduerp.modules.teachers.web;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.teachers.dto.CreateTeacherProfileRequest;
import com.eduerp.modules.teachers.dto.TeacherProfileResponse;
import com.eduerp.modules.teachers.dto.UpdateTeacherProfileRequest;
import com.eduerp.modules.teachers.usecase.CreateTeacherProfile;
import com.eduerp.modules.teachers.usecase.ListTeacherProfiles;
import com.eduerp.modules.teachers.usecase.UpdateTeacherProfile;
import com.eduerp.shared.AccountPrincipal;
import com.eduerp.shared.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teachers/profiles")
class TeacherAdminController {

    private final ListTeacherProfiles listTeacherProfiles;
    private final CreateTeacherProfile createTeacherProfile;
    private final UpdateTeacherProfile updateTeacherProfile;

    TeacherAdminController(ListTeacherProfiles listTeacherProfiles, CreateTeacherProfile createTeacherProfile,
            UpdateTeacherProfile updateTeacherProfile) {
        this.listTeacherProfiles = listTeacherProfiles;
        this.createTeacherProfile = createTeacherProfile;
        this.updateTeacherProfile = updateTeacherProfile;
    }

    @GetMapping
    @PreAuthorize(AccessConstants.AccessRules.READ_TEACHER)
    PageResponse<TeacherProfileResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return listTeacherProfiles.execute(pageable);
    }

    @PostMapping
    @PreAuthorize(AccessConstants.AccessRules.CREATE_TEACHER)
    UUID create(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody CreateTeacherProfileRequest request) {
        return createTeacherProfile.execute(principal.accountId(), principal.homeBranchId(), request);
    }

    @PatchMapping("/{profileId}")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_TEACHER)
    void update(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId,
            @Valid @RequestBody UpdateTeacherProfileRequest request) {
        updateTeacherProfile.execute(profileId, principal.accountId(), principal.homeBranchId(), request);
    }
}
