package com.eduerp.modules.courses.web;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.courses.dto.ClassResponse;
import com.eduerp.modules.courses.usecase.ListMyTeachingClasses;
import com.eduerp.shared.AccountPrincipal;
import com.eduerp.shared.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cổng giảng dạy: các lớp mà CHÍNH giáo viên đang đăng nhập được phân công dạy (đề cương ĐATN -
 * "Giảng viên xem danh sách lớp"). KHÁC {@code ClassAdminController} (đòi {@code READ_CLASS} scope
 * ORGANIZATION - chỉ admin): ở đây {@code teacherId} luôn là {@code principal.accountId()}, không
 * bao giờ nhận từ client, nên không dùng được để xem lớp của giáo viên khác.
 */
@RestController
@RequestMapping("/api/courses/me")
class TeachingSelfServiceController {

    private final ListMyTeachingClasses listClasses;

    TeachingSelfServiceController(ListMyTeachingClasses listClasses) {
        this.listClasses = listClasses;
    }

    @GetMapping("/classes")
    @PreAuthorize(AccessConstants.AccessRules.READ_TEACHING)
    PageResponse<ClassResponse> myClasses(@AuthenticationPrincipal AccountPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return listClasses.execute(principal.accountId(), pageable);
    }
}
