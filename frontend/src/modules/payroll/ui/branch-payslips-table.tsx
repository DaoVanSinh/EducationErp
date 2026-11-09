import { Can } from "@/entities/permission";
import { PAYSLIP_PAYMENT_METHOD_LABEL, type BranchPayslip } from "@/entities/payroll";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { formatter } from "@/shared/lib/format";
import { Badge } from "@/shared/ui/badge";
import { GlassButton } from "@/shared/ui/glass-button";
import { Banknote, Undo2 } from "lucide-react";

export interface BranchPayslipsTableProps {
  readonly rows: readonly BranchPayslip[];
  readonly onPay: (row: BranchPayslip) => void;
  readonly onUndo: (row: BranchPayslip) => void;
}

function paymentText(row: BranchPayslip): string {
  const { paidOn, paymentMethod } = row.payslip;
  if (paidOn === null) {
    return "Chưa trả";
  }
  const method = paymentMethod === null ? "" : ` · ${PAYSLIP_PAYMENT_METHOD_LABEL[paymentMethod]}`;
  return `${formatter.date(paidOn)}${method}`;
}

/** Số tiền đã chốt lúc duyệt, ở đây chỉ đọc. Thanh toán đủ một lần cho từng phiếu. */
export function BranchPayslipsTable({ rows, onPay, onUndo }: BranchPayslipsTableProps) {
  return (
    <ul className="flex flex-col gap-2">
      {rows.map((row) => (
        <li
          key={row.payslip.id}
          className="glass grid grid-cols-1 items-center gap-3 rounded-2xl p-3 lg:grid-cols-[minmax(0,2fr)_auto_minmax(0,1fr)_auto]"
        >
          <div className="min-w-0">
            <p className="truncate text-sm font-medium text-mist-100">
              {row.payslip.accountFullName ?? "Chưa rõ tên"}
            </p>
            <p className="truncate text-xs text-mist-500">
              Tháng {row.month}/{row.year} · {row.payslip.branchName ?? "Chưa thuộc chi nhánh nào"}
            </p>
            {row.payslip.paidOn === null && row.payslip.paymentNote ? (
              <p className="truncate text-xs text-amber-600" title={row.payslip.paymentNote}>
                Đã huỷ đánh dấu: {row.payslip.paymentNote}
              </p>
            ) : null}
          </div>
          <p className="text-sm font-semibold tabular-nums text-mist-100">{formatter.money(row.payslip.netPay)}</p>
          <div className="flex flex-wrap items-center gap-2">
            <Badge tone={row.payslip.paidOn === null ? "warning" : "positive"}>
              {row.payslip.paidOn === null ? "Chờ thanh toán" : "Đã thanh toán"}
            </Badge>
            <span className="text-xs text-mist-500">{paymentText(row)}</span>
          </div>
          <PayslipRowAction row={row} onPay={onPay} onUndo={onUndo} />
        </li>
      ))}
    </ul>
  );
}

function PayslipRowAction({
  row,
  onPay,
  onUndo,
}: {
  readonly row: BranchPayslip;
  readonly onPay: (row: BranchPayslip) => void;
  readonly onUndo: (row: BranchPayslip) => void;
}) {
  if (row.payslip.paidOn === null) {
    return (
      <Can {...ACCESS_RULE.payBranchPayslips}>
        <GlassButton size="sm" onClick={() => onPay(row)} icon={<Banknote size={14} aria-hidden />}>
          Đánh dấu đã trả
        </GlassButton>
      </Can>
    );
  }
  return (
    <Can {...ACCESS_RULE.updatePayroll}>
      <GlassButton variant="ghost" size="sm" onClick={() => onUndo(row)} icon={<Undo2 size={14} aria-hidden />}>
        Huỷ đánh dấu
      </GlassButton>
    </Can>
  );
}
