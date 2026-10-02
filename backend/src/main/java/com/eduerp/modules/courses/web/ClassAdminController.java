package com.eduerp.modules.courses.web;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.courses.dto.ClassResponse;
import com.eduerp.modules.courses.dto.CreateClassRequest;
import com.eduerp.modules.courses.dto.UpdateClassRequest;
import com.eduerp.modules.courses.usecase.CreateClass;
import com.eduerp.modules.courses.usecase.ListClasses;
import com.eduerp.modules.courses.usecase.UpdateClass;
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
@RequestMapping("/api/courses/classes")
class ClassAdminController {

    private final ListClasses listClasses;
    private final CreateClass createClass;
    private final UpdateClass updateClass;

    ClassAdminController(ListClasses listClasses, CreateClass createClass, UpdateClass updateClass) {
        this.listClasses = listClasses;
        this.createClass = createClass;
        this.updateClass = updateClass;
    }

    @GetMapping
    @PreAuthorize(AccessConstants.AccessRules.READ_CLASS)
    PageResponse<ClassResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return listClasses.execute(pageable);
    }

    @PostMapping
    @PreAuthorize(AccessConstants.AccessRules.CREATE_CLASS)
    UUID create(@AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody CreateClassRequest request) {
        return createClass.execute(principal.accountId(), principal.homeBranchId(), request);
    }

    @PatchMapping("/{classId}")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_CLASS)
    void update(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID classId,
            @Valid @RequestBody UpdateClassRequest request) {
        updateClass.execute(classId, principal.accountId(), principal.homeBranchId(), request);
    }
}
