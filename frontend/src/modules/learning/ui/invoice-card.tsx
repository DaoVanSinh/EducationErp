import {
  INVOICE_STATUS_LABEL,
  PAYMENT_METHOD_LABEL,
  type InvoiceDetail,
} from "@/entities/billing";
import { invoiceTone } from "@/modules/learning/model/learning-rules";
import { formatter } from "@/shared/lib/format";
import { Badge } from "@/shared/ui/badge";
import { GlassPanel } from "@/shared/ui/glass-panel";

export interface InvoiceCardProps {
  readonly detail: InvoiceDetail;
  readonly courseName: string | null;
}

/** Một hoá đơn học phí + lịch sử thanh toán của chính học viên (chỉ xem, không thao tác). */
export function InvoiceCard({ detail, courseName }: InvoiceCardProps) {
  const { invoice, payments } = detail;
  return (
    <GlassPanel className="flex flex-col gap-3">
      <header className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <h2 className="truncate text-base font-semibold text-mist-100">{courseName ?? "Học phí combo khóa học"}</h2>
          <p className="text-xs text-mist-400">
            Đợt {invoice.installmentNumber} · Hạn đóng {formatter.date(invoice.dueDate)}
          </p>
        </div>
        <Badge tone={invoiceTone(invoice.status)}>{INVOICE_STATUS_LABEL[invoice.status]}</Badge>
      </header>

      <dl className="grid grid-cols-2 gap-2 text-sm sm:grid-cols-3">
        <Amount label="Phải đóng" value={invoice.amount} />
        <Amount label="Đã đóng" value={invoice.amountPaid} />
        <Amount label="Còn lại" value={Math.max(invoice.amount - invoice.amountPaid, 0)} />
      </dl>

      <PaymentHistory payments={payments} />
    </GlassPanel>
  );
}

function Amount({ label, value }: { readonly label: string; readonly value: number }) {
  return (
    <div className="glass rounded-xl px-3 py-2">
      <dt className="text-xs text-mist-400">{label}</dt>
      <dd className="font-semibold text-mist-100">{formatter.money(value)}</dd>
    </div>
  );
}

function PaymentHistory({ payments }: { readonly payments: InvoiceDetail["payments"] }) {
  if (payments.length === 0) {
    return <p className="text-xs text-mist-400">Chưa có lần thanh toán nào.</p>;
  }
  return (
    <ul className="flex flex-col gap-1">
      {payments.map((payment) => (
        <li key={payment.id} className="flex flex-wrap justify-between gap-2 text-xs text-mist-400">
          <span>
            {formatter.dateTime(payment.paidAt ?? payment.createdAt)} · {PAYMENT_METHOD_LABEL[payment.method]}
          </span>
          <span className="font-semibold text-mist-100">{formatter.money(payment.amount)}</span>
        </li>
      ))}
    </ul>
  );
}
