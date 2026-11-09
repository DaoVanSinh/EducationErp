import { payBasisText, teachingText, type Payslip } from "@/entities/payroll";
import { PAY_TYPE_LABEL } from "@/shared/constants/pay-type";
import { formatter } from "@/shared/lib/format";
import { GlassButton } from "@/shared/ui/glass-button";
import { Pencil } from "lucide-react";

export interface PayslipsTableProps {
  readonly rows: readonly Payslip[];
  readonly periodEnd: string;
  readonly editable: boolean;
  readonly onAdjust: (payslip: Payslip) => void;
}

const AMOUNT_CELL = "px-2 py-3 text-right tabular-nums whitespace-nowrap";

/** Bảng rộng cuộn ngang trong khung riêng, để trang không bị tràn trên màn hình nhỏ. */
export function PayslipsTable({ rows, periodEnd, editable, onAdjust }: PayslipsTableProps) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[960px] text-sm">
        <thead>
          <tr className="text-left text-xs text-mist-500 uppercase">
            <th className="px-2 py-2 font-medium">Giáo viên</th>
            <th className="px-2 py-2 font-medium">Căn cứ tính</th>
            <th className="px-2 py-2 text-right font-medium">Lương chính</th>
            <th className="px-2 py-2 text-right font-medium">Phụ cấp</th>
            <th className="px-2 py-2 text-right font-medium">Thưởng/điều chỉnh</th>
            <th className="px-2 py-2 text-right font-medium">BHXH 10,5%</th>
            <th className="px-2 py-2 text-right font-medium">Thuế TNCN</th>
            <th className="px-2 py-2 text-right font-medium">Thực nhận</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((payslip) => (
            <tr key={payslip.id} className="border-t border-slate-200/70 align-top">
              <td className="px-2 py-3">
                <p className="text-mist-100">{payslip.accountFullName ?? "Chưa rõ tên"}</p>
                <p className="text-xs text-mist-500">{PAY_TYPE_LABEL[payslip.payType]}</p>
              </td>
              <td className="px-2 py-3">
                <p className="text-mist-100">{payBasisText(payslip, periodEnd)}</p>
                <p className="text-xs text-mist-500">{teachingText(payslip)}</p>
              </td>
              <td className={AMOUNT_CELL}>{formatter.money(payslip.grossPay)}</td>
              <td className={AMOUNT_CELL}>{formatter.money(payslip.allowancesTotal)}</td>
              <td className={AMOUNT_CELL}>
                <AdjustmentCell payslip={payslip} editable={editable} onAdjust={onAdjust} />
              </td>
              <td className={AMOUNT_CELL}>{formatter.money(payslip.socialInsuranceEmployee)}</td>
              <td className={AMOUNT_CELL}>{formatter.money(payslip.incomeTaxWithheld)}</td>
              <td className={`${AMOUNT_CELL} font-semibold text-mist-100`}>{formatter.money(payslip.netPay)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function AdjustmentCell({
  payslip,
  editable,
  onAdjust,
}: {
  readonly payslip: Payslip;
  readonly editable: boolean;
  readonly onAdjust: (payslip: Payslip) => void;
}) {
  return (
    <div className="flex flex-col items-end gap-1">
      <span>{formatter.money(payslip.adjustment)}</span>
      {payslip.note ? <span className="max-w-40 truncate text-xs text-mist-500" title={payslip.note}>{payslip.note}</span> : null}
      {editable ? (
        <GlassButton
          variant="ghost"
          size="sm"
          aria-label={`Điều chỉnh lương của ${payslip.accountFullName ?? "giáo viên"}`}
          onClick={() => onAdjust(payslip)}
          icon={<Pencil size={14} aria-hidden />}
        >
          Sửa
        </GlassButton>
      ) : null}
    </div>
  );
}
