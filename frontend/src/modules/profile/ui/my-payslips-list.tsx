import {
  payBasisText,
  PAYSLIP_PAYMENT_METHOD_LABEL,
  teachingText,
  useMyPayslips,
  type MyPayslip,
} from "@/entities/payroll";
import { PAY_TYPE_LABEL } from "@/shared/constants/pay-type";
import { DEFAULT_PAGE_SIZE } from "@/shared/constants/query-config";
import { formatter } from "@/shared/lib/format";
import { Badge } from "@/shared/ui/badge";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { Pagination } from "@/shared/ui/pagination";
import { Skeleton } from "@/shared/ui/skeleton";
import { Wallet } from "lucide-react";
import { useState } from "react";

/** Phiếu lương CỦA CHÍNH tài khoản đang đăng nhập, chỉ từ kỳ đã duyệt - không cần quyền READ_PAYROLL. */
export function MyPayslipsList() {
  const [page, setPage] = useState(0);
  const payslips = useMyPayslips(page, DEFAULT_PAGE_SIZE);

  if (payslips.isError) {
    return <ErrorNotice error={payslips.error} />;
  }
  if (payslips.isPending) {
    return <Skeleton className="h-16" />;
  }
  if (payslips.data.items.length === 0) {
    return (
      <EmptyState
        icon={<Wallet size={28} aria-hidden />}
        title="Chưa có phiếu lương nào"
        description="Phiếu lương hiện ở đây sau khi kỳ lương được duyệt."
      />
    );
  }

  return (
    <div className="flex flex-col gap-3">
      <ul className="flex flex-col gap-2">
        {payslips.data.items.map((payslip) => (
          <MyPayslipRow key={payslip.id} payslip={payslip} />
        ))}
      </ul>
      <Pagination
        page={payslips.data.page}
        totalPages={payslips.data.totalPages}
        totalItems={payslips.data.totalItems}
        onPageChange={setPage}
        itemLabel="phiếu lương"
      />
    </div>
  );
}

function paymentStatusText(payslip: MyPayslip): string {
  if (payslip.paidOn === null) {
    return "Chờ thanh toán";
  }
  const method = payslip.paymentMethod === null ? "" : ` · ${PAYSLIP_PAYMENT_METHOD_LABEL[payslip.paymentMethod]}`;
  return `Đã thanh toán ${formatter.date(payslip.paidOn)}${method}`;
}

function MyPayslipRow({ payslip }: { readonly payslip: MyPayslip }) {
  const lines = [
    { label: "Lương chính", value: payslip.grossPay },
    { label: "Phụ cấp", value: payslip.allowancesTotal },
    { label: "Thưởng/điều chỉnh", value: payslip.adjustment },
    { label: "Trừ BHXH", value: payslip.socialInsuranceEmployee },
    { label: "Trừ thuế TNCN", value: payslip.incomeTaxWithheld },
  ];
  return (
    <li className="glass flex flex-col gap-3 rounded-2xl p-3">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="text-sm font-medium text-mist-100">
            Tháng {payslip.month}/{payslip.year}
          </p>
          <p className="text-xs text-mist-500">
            {formatter.date(payslip.periodStart)} - {formatter.date(payslip.periodEnd)} · {PAY_TYPE_LABEL[payslip.payType]}
          </p>
        </div>
        <div className="flex items-center gap-2">
          {payslip.probationDays > 0 ? <Badge tone="neutral">Có ngày thử việc</Badge> : null}
          <Badge tone={payslip.paidOn === null ? "warning" : "positive"}>{paymentStatusText(payslip)}</Badge>
          <p className="text-sm font-semibold text-mist-100 tabular-nums">{formatter.money(payslip.netPay)} thực nhận</p>
        </div>
      </div>
      <p className="text-xs text-mist-400">
        {payBasisText(payslip, payslip.periodEnd)} · {teachingText(payslip)}
      </p>
      <dl className="grid grid-cols-2 gap-2 sm:grid-cols-5">
        {lines.map((line) => (
          <div key={line.label}>
            <dt className="text-xs text-mist-500">{line.label}</dt>
            <dd className="text-sm text-mist-100 tabular-nums">{formatter.money(line.value)}</dd>
          </div>
        ))}
      </dl>
      {payslip.note ? <p className="text-xs text-mist-400">Ghi chú: {payslip.note}</p> : null}
    </li>
  );
}
