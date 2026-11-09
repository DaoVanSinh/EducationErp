import { PAYROLL_RUN_STATUS, PAYROLL_RUN_STATUS_LABEL, type PayrollRunSummary } from "@/entities/payroll";
import { buildPayrollRunDetailPath } from "@/shared/constants/app-routes";
import { formatter } from "@/shared/lib/format";
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
          className="glass grid grid-cols-1 items-center gap-3 rounded-2xl p-3 lg:grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)_auto]"
        >
          <div className="min-w-0">
            <p className="truncate text-sm text-mist-100">
              Tháng {run.month}/{run.year}
            </p>
            <p className="truncate text-xs text-mist-500">
              {formatter.date(run.periodStart)} - {formatter.date(run.periodEnd)}
            </p>
          </div>
          <Badge tone={run.status === PAYROLL_RUN_STATUS.approved ? "positive" : "neutral"}>
            {PAYROLL_RUN_STATUS_LABEL[run.status]}
          </Badge>
          <div className="min-w-0">
            <p className="truncate text-sm text-mist-100 tabular-nums">{formatter.money(run.totalNetPay)} thực nhận</p>
            <p className="truncate text-xs text-mist-500">{run.payslipCount} phiếu lương</p>
          </div>
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
