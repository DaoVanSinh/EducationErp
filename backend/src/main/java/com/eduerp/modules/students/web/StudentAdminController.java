package com.eduerp.modules.students.web;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.students.dto.CreateStudentRequest;
import com.eduerp.modules.students.dto.StudentProfileResponse;
import com.eduerp.modules.students.dto.UpdateStudentProfileRequest;
import com.eduerp.modules.students.usecase.CreateStudent;
import com.eduerp.modules.students.usecase.GetStudentProfile;
import com.eduerp.modules.students.usecase.ListStudentProfiles;
import com.eduerp.modules.students.usecase.UpdateStudentProfile;
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
@RequestMapping("/api/students/profiles")
class StudentAdminController {

    private final ListStudentProfiles listStudentProfiles;
    private final GetStudentProfile getStudentProfile;
    private final CreateStudent createStudent;
    private final UpdateStudentProfile updateStudentProfile;

    StudentAdminController(ListStudentProfiles listStudentProfiles, GetStudentProfile getStudentProfile,
            CreateStudent createStudent, UpdateStudentProfile updateStudentProfile) {
        this.listStudentProfiles = listStudentProfiles;
        this.getStudentProfile = getStudentProfile;
        this.createStudent = createStudent;
        this.updateStudentProfile = updateStudentProfile;
    }

    @GetMapping
    @PreAuthorize(AccessConstants.AccessRules.READ_STUDENT)
    PageResponse<StudentProfileResponse> list(@AuthenticationPrincipal AccountPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return listStudentProfiles.execute(principal.accountId(), pageable);
    }

    @GetMapping("/{profileId}")
    @PreAuthorize(AccessConstants.AccessRules.READ_STUDENT)
    StudentProfileResponse get(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId) {
        return getStudentProfile.execute(principal.accountId(), profileId);
    }

    /** Tạo tài khoản + hồ sơ học viên trong một lần gọi; trả về id HỒ SƠ (khoá dùng để ghi danh). */
    @PostMapping
    @PreAuthorize(AccessConstants.AccessRules.CREATE_STUDENT)
    UUID create(@AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody CreateStudentRequest request) {
        return createStudent.execute(principal.accountId(), principal.homeBranchId(), request);
    }

    @PatchMapping("/{profileId}")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_STUDENT)
    void update(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId,
            @Valid @RequestBody UpdateStudentProfileRequest request) {
        updateStudentProfile.execute(profileId, principal.accountId(), principal.homeBranchId(), request);
    }
}
