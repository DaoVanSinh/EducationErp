import type { PayrollRunSummary } from "@/entities/payroll";
import { formatter } from "@/shared/lib/format";

export function PayrollRunTotals({ run }: { readonly run: PayrollRunSummary }) {
  const items = [
    { label: "Số phiếu lương", value: formatter.count(run.payslipCount) },
    { label: "Tổng lương chính", value: formatter.money(run.totalGrossPay) },
    { label: "Tổng thực nhận", value: formatter.money(run.totalNetPay) },
    { label: "BHXH trung tâm đóng", value: formatter.money(run.totalEmployerInsurance) },
  ];
  return (
    <dl className="grid grid-cols-2 gap-3 lg:grid-cols-4">
      {items.map((item) => (
        <div key={item.label} className="glass rounded-2xl p-3">
          <dt className="text-xs text-mist-500">{item.label}</dt>
          <dd className="text-base font-semibold text-mist-100 tabular-nums">{item.value}</dd>
        </div>
      ))}
    </dl>
  );
}
