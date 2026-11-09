package com.eduerp.modules.payroll.internal.response;

import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.payroll.dto.BranchPayrollSummary;
import com.eduerp.modules.payroll.dto.MyPayslipResponse;
import com.eduerp.modules.payroll.dto.PayrollRunResponse;
import com.eduerp.modules.payroll.dto.PayslipResponse;
import com.eduerp.modules.payroll.internal.model.PayrollRun;
import com.eduerp.modules.payroll.internal.model.Payslip;
import com.eduerp.modules.payroll.internal.repository.PayrollRunAggregateRow;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** Một chỗ dựng DTO của kỳ lương và phiếu lương, để mọi màn hình cùng thấy một ý nghĩa của mỗi con số. */
public final class PayrollResponseAssembler {

    private PayrollResponseAssembler() {
    }

    public static PayrollRunResponse toRunResponse(PayrollRun run, List<Payslip> payslips) {
        return toRunResponse(run, payslips.size(), sum(payslips, Payslip::getGrossPay),
                sum(payslips, Payslip::getNetPay), sum(payslips, Payslip::getSocialInsuranceEmployer));
    }

    /** {@code aggregate} null = kỳ chưa có phiếu nào, truy vấn gộp không trả dòng cho kỳ đó. */
    public static PayrollRunResponse toRunResponse(PayrollRun run, PayrollRunAggregateRow aggregate) {
        if (aggregate == null) {
            return toRunResponse(run, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        return toRunResponse(run, Math.toIntExact(aggregate.getPayslipCount()), aggregate.getTotalGrossPay(),
                aggregate.getTotalNetPay(), aggregate.getTotalEmployerInsurance());
    }

    public static PayslipResponse toPayslipResponse(Payslip payslip,
            Map<UUID, IdentityManagement.AccountBasicInfo> accountInfo, Map<UUID, String> branchNames) {
        var account = accountInfo.get(payslip.getAccountId());
        return new PayslipResponse(payslip.getId(), payslip.getAccountId(),
                account == null ? null : account.fullName(), payslip.getPayType(), payslip.getPayRate(),
                payslip.getSessionCount(), payslip.getHoursWorked(), payslip.getPaidDays(),
                payslip.getProbationDays(), payslip.getDependentCount(), payslip.getGrossPay(),
                payslip.getAllowancesTotal(), payslip.getAdjustment(), payslip.getSocialInsuranceEmployee(),
                payslip.getSocialInsuranceEmployer(), payslip.getIncomeTaxWithheld(), payslip.getNetPay(),
                payslip.getNote(), payslip.getBranchId(), branchNameOf(payslip.getBranchId(), branchNames),
                payslip.getPaidOn(), payslip.getPaymentMethod(), payslip.getPaymentNote());
    }

    public static MyPayslipResponse toMyPayslipResponse(Payslip payslip) {
        var period = payslip.getPayrollRun().getPeriod();
        return new MyPayslipResponse(payslip.getId(), period.getYear(), period.getMonthValue(), period.atDay(1),
                period.atEndOfMonth(), payslip.getPayType(), payslip.getPayRate(), payslip.getSessionCount(),
                payslip.getHoursWorked(), payslip.getPaidDays(), payslip.getProbationDays(), payslip.getGrossPay(),
                payslip.getAllowancesTotal(), payslip.getAdjustment(), payslip.getSocialInsuranceEmployee(),
                payslip.getIncomeTaxWithheld(), payslip.getNetPay(), payslip.getNote(), payslip.getPaidOn(),
                payslip.getPaymentMethod());
    }

    /** Tiến độ chi trả theo chi nhánh của một kỳ. Phiếu chưa thuộc chi nhánh nào gom vào một dòng riêng. */
    public static List<BranchPayrollSummary> toBranchSummaries(List<Payslip> payslips, Map<UUID, String> branchNames) {
        var byBranch = new LinkedHashMap<UUID, List<Payslip>>();
        payslips.forEach(payslip -> byBranch.computeIfAbsent(payslip.getBranchId(), id -> new ArrayList<>())
                .add(payslip));
        return byBranch.entrySet().stream()
                .map(entry -> new BranchPayrollSummary(entry.getKey(), branchNameOf(entry.getKey(), branchNames),
                        entry.getValue().size(), Math.toIntExact(entry.getValue().stream().filter(Payslip::isPaid).count()),
                        sum(entry.getValue(), Payslip::getNetPay)))
                .sorted(Comparator.comparing(BranchPayrollSummary::branchName,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** Map.of() ném NPE khi tra khoá null, mà phiếu của giáo viên chưa có chi nhánh có branchId null. */
    private static String branchNameOf(UUID branchId, Map<UUID, String> branchNames) {
        return branchId == null ? null : branchNames.get(branchId);
    }

    private static PayrollRunResponse toRunResponse(PayrollRun run, int payslipCount, BigDecimal totalGrossPay,
            BigDecimal totalNetPay, BigDecimal totalEmployerInsurance) {
        var period = run.getPeriod();
        return new PayrollRunResponse(run.getId(), run.getYear(), run.getMonth(), period.atDay(1),
                period.atEndOfMonth(), run.getStatus(), run.getRejectionReason(), payslipCount, totalGrossPay,
                totalNetPay, totalEmployerInsurance);
    }

    private static BigDecimal sum(List<Payslip> payslips, Function<Payslip, BigDecimal> amount) {
        return payslips.stream().map(amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
