package com.eduerp.modules.payroll.internal;

import com.eduerp.modules.attendance.AttendanceManagement;
import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.payroll.internal.model.PayrollRun;
import com.eduerp.modules.payroll.internal.model.Payslip;
import com.eduerp.modules.payroll.internal.repository.PayslipRepository;
import com.eduerp.modules.payroll.internal.rules.PayInput;
import com.eduerp.modules.payroll.internal.rules.PayrollRules;
import com.eduerp.modules.payroll.internal.rules.PayslipFigures;
import com.eduerp.modules.teachers.TeachersManagement;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Đồng bộ phiếu lương của một kỳ với điều khoản lương và điểm danh hiện tại. Dùng chung cho tạo kỳ, tính lại và
 * gửi duyệt, nên cả ba luôn cho cùng một kết quả. Thưởng/điều chỉnh và ghi chú của kế toán được giữ nguyên.
 */
@Component
public class PayslipSynchronizer {

    private static final AttendanceManagement.TeachingSummary NO_TEACHING =
            new AttendanceManagement.TeachingSummary(null, 0, 0);

    private final PayslipRepository payslips;
    private final TeachersManagement teachers;
    private final AttendanceManagement attendance;
    private final IdentityManagement identity;

    PayslipSynchronizer(PayslipRepository payslips, TeachersManagement teachers, AttendanceManagement attendance,
            IdentityManagement identity) {
        this.identity = identity;
        this.payslips = payslips;
        this.teachers = teachers;
        this.attendance = attendance;
    }

    /** Trả về đúng các phiếu còn lại của kỳ. Phiếu của người không còn gì để trả (vd sửa ngày bắt đầu) bị xoá. */
    public List<Payslip> synchronize(PayrollRun run) {
        var teaching = teachingOf(run.getPeriod());
        var stale = new HashMap<UUID, Payslip>();
        payslips.findAllByPayrollRun_Id(run.getId()).forEach(payslip -> stale.put(payslip.getAccountId(), payslip));

        var kept = new ArrayList<Payslip>();
        for (var terms : teachers.listPayTerms()) {
            var current = stale.get(terms.accountId());
            var input = toInput(terms, teaching.getOrDefault(terms.accountId(), NO_TEACHING), current);
            PayrollRules.calculate(input, run.getPeriod()).ifPresent(figures -> {
                stale.remove(terms.accountId());
                kept.add(upsert(run, terms.accountId(), current, figures));
            });
        }
        payslips.deleteAll(stale.values());
        var homeBranches = identity.homeBranchesOf(kept.stream().map(Payslip::getAccountId).toList());
        kept.forEach(payslip -> payslip.assignBranch(homeBranches.get(payslip.getAccountId())));
        return payslips.saveAll(kept);
    }

    /**
     * Giáo viên chưa thiết lập lương cùng số buổi đã dạy trong kỳ. Số buổi lớn hơn 0 là người sẽ bị bỏ sót lương,
     * chặn gửi duyệt; bằng 0 là chưa dạy gì, chỉ cảnh báo.
     */
    public Map<UUID, Integer> missingPayTerms(YearMonth period) {
        var missing = new HashMap<UUID, Integer>();
        teachers.listActiveAccountsWithoutPayTerms().forEach(accountId -> missing.put(accountId, 0));
        teachingOf(period).values()
                .forEach(summary -> missing.put(summary.teacherAccountId(), summary.sessionCount()));
        teachers.listPayTerms().forEach(terms -> missing.remove(terms.accountId()));
        return missing;
    }

    private static PayInput toInput(TeachersManagement.PayTerms terms, AttendanceManagement.TeachingSummary teaching,
            Payslip current) {
        var adjustment = current == null ? BigDecimal.ZERO : current.getAdjustment();
        return new PayInput(terms.payType(), terms.monthlySalary(), terms.hourlyRate(), terms.workStartDate(),
                terms.probationEndDate(), terms.lastWorkDate(), terms.dependentCount(), terms.allowancesTotal(),
                teaching.sessionCount(), teaching.totalMinutes(), adjustment);
    }

    private static Payslip upsert(PayrollRun run, UUID accountId, Payslip current, PayslipFigures figures) {
        if (current == null) {
            return new Payslip(run, accountId, figures);
        }
        current.apply(figures);
        return current;
    }

    private Map<UUID, AttendanceManagement.TeachingSummary> teachingOf(YearMonth period) {
        return attendance.teachingSummaries(period.atDay(1), period.atEndOfMonth());
    }
}
