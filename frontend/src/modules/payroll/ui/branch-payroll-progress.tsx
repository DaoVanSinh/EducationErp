import type { BranchPayrollSummary } from "@/entities/payroll";
import { formatter } from "@/shared/lib/format";
import { Badge } from "@/shared/ui/badge";
import { GlassPanel } from "@/shared/ui/glass-panel";

/** Tiến độ chi trả của kỳ đã duyệt theo từng chi nhánh, để admin gốc biết chi nhánh nào chưa trả xong. */
export function BranchPayrollProgress({ summaries }: { readonly summaries: readonly BranchPayrollSummary[] }) {
  if (summaries.length === 0) {
    return null;
  }
  return (
    <GlassPanel className="flex flex-col gap-3">
      <h2 className="text-sm font-semibold text-mist-100">Tiến độ chi trả theo chi nhánh</h2>
      <ul className="flex flex-col gap-2">
        {summaries.map((summary) => (
          <li
            key={summary.branchId ?? "none"}
            className="glass grid grid-cols-1 items-center gap-2 rounded-2xl p-3 sm:grid-cols-[minmax(0,1fr)_auto_auto]"
          >
            <p className="truncate text-sm text-mist-100">{summary.branchName ?? "Chưa thuộc chi nhánh nào"}</p>
            <p className="text-sm tabular-nums text-mist-100">{formatter.money(summary.totalNetPay)}</p>
            <Badge tone={summary.paidCount === summary.payslipCount ? "positive" : "warning"}>
              Đã trả {summary.paidCount}/{summary.payslipCount}
            </Badge>
          </li>
        ))}
      </ul>
    </GlassPanel>
  );
}
