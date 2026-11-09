package com.eduerp.modules.teachers.web;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.teachers.dto.CreateTeacherRequest;
import com.eduerp.modules.teachers.dto.LeaveTeacherRequest;
import com.eduerp.modules.teachers.dto.TeacherBranchChangeResponse;
import com.eduerp.modules.teachers.dto.TeacherPayResponse;
import com.eduerp.modules.teachers.dto.TeacherPayTermsRequest;
import com.eduerp.modules.teachers.dto.TeacherProfileResponse;
import com.eduerp.modules.teachers.dto.UpdateTeacherProfileRequest;
import com.eduerp.modules.teachers.usecase.CreateTeacher;
import com.eduerp.modules.teachers.usecase.GetTeacherBranchHistory;
import com.eduerp.modules.teachers.usecase.GetTeacherPay;
import com.eduerp.modules.teachers.usecase.GetTeacherProfile;
import com.eduerp.modules.teachers.usecase.LeaveTeacher;
import com.eduerp.modules.teachers.usecase.ListTeacherProfiles;
import com.eduerp.modules.teachers.usecase.UpdateTeacherPay;
import com.eduerp.modules.teachers.usecase.UpdateTeacherProfile;
import com.eduerp.shared.AccountPrincipal;
import com.eduerp.shared.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teachers/profiles")
class TeacherAdminController {

    private final ListTeacherProfiles listTeacherProfiles;
    private final GetTeacherProfile getTeacherProfile;
    private final CreateTeacher createTeacher;
    private final UpdateTeacherProfile updateTeacherProfile;
    private final GetTeacherPay getTeacherPay;
    private final UpdateTeacherPay updateTeacherPay;
    private final LeaveTeacher leaveTeacher;
    private final GetTeacherBranchHistory getTeacherBranchHistory;

    TeacherAdminController(ListTeacherProfiles listTeacherProfiles, GetTeacherProfile getTeacherProfile,
            CreateTeacher createTeacher, UpdateTeacherProfile updateTeacherProfile, GetTeacherPay getTeacherPay,
            UpdateTeacherPay updateTeacherPay, LeaveTeacher leaveTeacher,
            GetTeacherBranchHistory getTeacherBranchHistory) {
        this.leaveTeacher = leaveTeacher;
        this.getTeacherBranchHistory = getTeacherBranchHistory;
        this.listTeacherProfiles = listTeacherProfiles;
        this.getTeacherProfile = getTeacherProfile;
        this.createTeacher = createTeacher;
        this.updateTeacherProfile = updateTeacherProfile;
        this.getTeacherPay = getTeacherPay;
        this.updateTeacherPay = updateTeacherPay;
    }

    @GetMapping
    @PreAuthorize(AccessConstants.AccessRules.READ_TEACHER)
    PageResponse<TeacherProfileResponse> list(@AuthenticationPrincipal AccountPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable, @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) String query) {
        return listTeacherProfiles.execute(principal.accountId(), pageable, branchId, query);
    }

    @GetMapping("/{profileId}")
    @PreAuthorize(AccessConstants.AccessRules.READ_TEACHER)
    TeacherProfileResponse get(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId) {
        return getTeacherProfile.execute(principal.accountId(), profileId);
    }

    @GetMapping("/{profileId}/branch-history")
    @PreAuthorize(AccessConstants.AccessRules.READ_TEACHER)
    List<TeacherBranchChangeResponse> branchHistory(@AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID profileId) {
        return getTeacherBranchHistory.execute(principal.accountId(), profileId);
    }

    @PostMapping("/{profileId}/leave")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_TEACHER)
    void leave(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId,
            @Valid @RequestBody LeaveTeacherRequest request) {
        leaveTeacher.execute(profileId, principal.accountId(), principal.homeBranchId(), request);
    }

    @PostMapping
    @PreAuthorize(AccessConstants.AccessRules.CREATE_TEACHER)
    UUID create(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody CreateTeacherRequest request) {
        return createTeacher.execute(principal.accountId(), principal.homeBranchId(), request);
    }

    @PatchMapping("/{profileId}")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_TEACHER)
    void update(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId,
            @Valid @RequestBody UpdateTeacherProfileRequest request) {
        updateTeacherProfile.execute(profileId, principal.accountId(), principal.homeBranchId(), request);
    }

    /** Mức lương gác bằng quyền lương, không phải quyền giáo viên: xem hồ sơ không có nghĩa là xem được lương. */
    @GetMapping("/{profileId}/pay")
    @PreAuthorize(AccessConstants.AccessRules.READ_PAYROLL)
    TeacherPayResponse getPay(@PathVariable UUID profileId) {
        return getTeacherPay.execute(profileId);
    }

    @PutMapping("/{profileId}/pay")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_PAYROLL)
    TeacherPayResponse updatePay(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId,
            @Valid @RequestBody TeacherPayTermsRequest request) {
        return updateTeacherPay.execute(profileId, principal.accountId(), principal.homeBranchId(), request);
    }
}
