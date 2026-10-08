import { PAYROLL_RUN_STATUS, PAYROLL_RUN_STATUS_LABEL, type PayrollRunSummary } from "@/entities/payroll";
import { buildPayrollRunDetailPath } from "@/shared/constants/app-routes";
import { Badge } from "@/shared/ui/badge";
import { GlassButton } from "@/shared/ui/glass-button";
import { ChevronRight } from "lucide-react";
import { Link } from "react-router-dom";

export interface PayrollRunsTableProps {
  readonly rows: readonly PayrollRunSummary[];
}

export function PayrollRunsTable({ rows }: PayrollRunsTableProps) {
  return (
    <ul className="flex flex-col gap-2">
      {rows.map((run) => (
        <li
          key={run.id}
          className="glass grid grid-cols-1 items-center gap-3 rounded-2xl p-3 lg:grid-cols-[minmax(0,1fr)_auto_auto_auto]"
        >
          <p className="truncate text-sm text-mist-100">Kỳ lương {run.month}/{run.year}</p>
          <Badge tone={run.status === PAYROLL_RUN_STATUS.approved ? "positive" : "neutral"}>
            {PAYROLL_RUN_STATUS_LABEL[run.status]}
          </Badge>
          <p className="text-xs text-mist-500">{run.payslipCount} phiếu lương</p>
          <Link to={buildPayrollRunDetailPath(run.id)}>
            <GlassButton variant="secondary" size="sm" icon={<ChevronRight size={14} aria-hidden />}>
              Xem chi tiết
            </GlassButton>
          </Link>
        </li>
      ))}
    </ul>
  );
}
