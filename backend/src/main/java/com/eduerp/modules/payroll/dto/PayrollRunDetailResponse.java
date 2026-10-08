package com.eduerp.modules.payroll.dto;

import java.util.List;

public record PayrollRunDetailResponse(PayrollRunResponse run, List<PayslipResponse> payslips) {
}
