package com.eduerp.modules.payroll.web;

import com.eduerp.modules.payroll.dto.ContractResponse;
import com.eduerp.modules.payroll.dto.MyPayslipResponse;
import com.eduerp.modules.payroll.usecase.ListContracts;
import com.eduerp.modules.payroll.usecase.ListMyPayslips;
import com.eduerp.shared.AccountPrincipal;
import com.eduerp.shared.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hợp đồng/phiếu lương CỦA CHÍNH người gọi, cho trang Hồ sơ của tôi - mirror
 * {@code AccountSelfServiceController} ("Không gắn @PreAuthorize: ai đăng nhập được thì đương
 * nhiên được biết mình là ai"). KHÁC {@code ContractAdminController}/{@code PayrollRunAdminController}
 * (đòi {@code READ_PAYROLL}, scope ORGANIZATION - chỉ admin xem được MỌI hợp đồng/phiếu lương): ở
 * đây {@code accountId} luôn là {@code principal.accountId()}, không bao giờ nhận từ client, nên
 * không có cách nào dùng endpoint này để xem dữ liệu của người khác - không cần một quyền nghiệp vụ
 * nào để gác.
 */
@RestController
@RequestMapping("/api/payroll/me")
class PayrollSelfServiceController {

    private final ListContracts listContracts;
    private final ListMyPayslips listMyPayslips;

    PayrollSelfServiceController(ListContracts listContracts, ListMyPayslips listMyPayslips) {
        this.listContracts = listContracts;
        this.listMyPayslips = listMyPayslips;
    }

    @GetMapping("/contracts")
    PageResponse<ContractResponse> myContracts(@AuthenticationPrincipal AccountPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return listContracts.execute(pageable, principal.accountId());
    }

    @GetMapping("/payslips")
    PageResponse<MyPayslipResponse> myPayslips(@AuthenticationPrincipal AccountPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return listMyPayslips.execute(principal.accountId(), pageable);
    }
}
