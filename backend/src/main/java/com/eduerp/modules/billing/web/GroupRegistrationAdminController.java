package com.eduerp.modules.billing.web;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.billing.dto.CreateGroupRegistrationRequest;
import com.eduerp.modules.billing.dto.GroupRegistrationResponse;
import com.eduerp.modules.billing.usecase.CancelGroupRegistration;
import com.eduerp.modules.billing.usecase.CreateGroupRegistration;
import com.eduerp.modules.billing.usecase.ListGroupRegistrations;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Đăng ký nhóm - cùng bộ quyền hoá đơn với combo (tạo: CREATE, xem: READ, huỷ: UPDATE). */
@RestController
@RequestMapping("/api/billing/group-registrations")
class GroupRegistrationAdminController {

    private final ListGroupRegistrations listGroups;
    private final CreateGroupRegistration createGroup;
    private final CancelGroupRegistration cancelGroup;

    GroupRegistrationAdminController(ListGroupRegistrations listGroups, CreateGroupRegistration createGroup,
            CancelGroupRegistration cancelGroup) {
        this.listGroups = listGroups;
        this.createGroup = createGroup;
        this.cancelGroup = cancelGroup;
    }

    @GetMapping
    @PreAuthorize(AccessConstants.AccessRules.READ_INVOICE)
    PageResponse<GroupRegistrationResponse> list(@AuthenticationPrincipal AccountPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable, @RequestParam(required = false) UUID courseId) {
        return listGroups.execute(principal.accountId(), pageable, courseId);
    }

    @PostMapping
    @PreAuthorize(AccessConstants.AccessRules.CREATE_INVOICE)
    GroupRegistrationResponse create(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody CreateGroupRegistrationRequest request) {
        return createGroup.execute(principal.accountId(), principal.homeBranchId(), request);
    }

    @PostMapping("/{groupId}/cancel")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_INVOICE)
    void cancel(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID groupId) {
        cancelGroup.execute(groupId, principal.accountId(), principal.homeBranchId());
    }
}
