import {
  PAYMENT_METHOD_LABEL,
  PAYMENT_STATUS,
  PAYMENT_STATUS_LABEL,
  type Payment,
} from "@/entities/billing";
import { formatter } from "@/shared/lib/format";
import { Badge } from "@/shared/ui/badge";
import { EmptyState } from "@/shared/ui/empty-state";
import { Banknote } from "lucide-react";

export interface PaymentsTableProps {
  readonly rows: readonly Payment[];
}

export function PaymentsTable({ rows }: PaymentsTableProps) {
  if (rows.length === 0) {
    return (
      <EmptyState
        icon={<Banknote size={28} aria-hidden />}
        title="Chưa có giao dịch nào"
        description="Hoá đơn này chưa nhận khoản thanh toán nào."
      />
    );
  }
  return (
    <ul className="flex flex-col gap-2">
      {rows.map((payment) => (
        <li
          key={payment.id}
          className="glass grid grid-cols-1 items-center gap-3 rounded-2xl p-3 lg:grid-cols-[minmax(0,1fr)_auto_auto_auto]"
        >
          <p className="truncate text-sm text-mist-100">{PAYMENT_METHOD_LABEL[payment.method]}</p>
          <p className="text-sm text-mist-100">{formatter.count(payment.amount)} đ</p>
          <Badge tone={payment.status === PAYMENT_STATUS.success ? "positive" : "neutral"}>
            {PAYMENT_STATUS_LABEL[payment.status]}
          </Badge>
          <p className="text-xs text-mist-500">{formatter.dateTime(payment.createdAt)}</p>
        </li>
      ))}
    </ul>
  );
}
