package com.eduerp.modules.payroll.dto;

import java.util.List;

/** {@code missingPayTerms} chỉ có khi kỳ còn nháp: kỳ đã gửi duyệt thì số liệu đã chốt. */
public record PayrollRunDetailResponse(PayrollRunResponse run, List<PayslipResponse> payslips,
        List<MissingPayTermsResponse> missingPayTerms, List<BranchPayrollSummary> branchSummaries) {
}
