import { INVOICE_STATUS, INVOICE_STATUS_LABEL, invoiceTitle, type InvoiceSummary } from "@/entities/billing";
import { buildInvoiceDetailPath } from "@/shared/constants/app-routes";
import { formatter } from "@/shared/lib/format";
import { staggerDelay } from "@/shared/lib/motion";
import { Badge } from "@/shared/ui/badge";
import { GlassButton } from "@/shared/ui/glass-button";
import { m } from "framer-motion";
import { ChevronRight } from "lucide-react";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";

export interface InvoicesTableProps {
  readonly rows: readonly InvoiceSummary[];
  /** Thao tác thêm trên từng dòng, dùng ở danh sách quá hạn. */
  readonly renderActions?: (invoice: InvoiceSummary) => ReactNode;
}

function toneOf(status: InvoiceSummary["status"]): "positive" | "neutral" | "danger" {
  if (status === INVOICE_STATUS.overdue) {
    return "danger";
  }
  return status === INVOICE_STATUS.paid ? "positive" : "neutral";
}

/**
 * Một hoá đơn neo vào combo hoặc vào ghi danh, không bao giờ cả hai (CHECK constraint ở backend).
 * Combo không có cột tên nên tên ghép từ các khoá trong combo. Tên khoá không tra được thì vẫn hiện
 * một nhãn chung, không bao giờ lộ mã ra giao diện.
 */
function sourceLabelOf(invoice: InvoiceSummary): string {
  const courses = invoice.courseNames.join(" + ");
  if (invoice.comboId !== null) {
    return courses.length === 0 ? "Combo khoá học" : `Combo ${courses}`;
  }
  return courses.length === 0 ? "Khoá học" : courses;
}

export function InvoicesTable({ rows, renderActions }: InvoicesTableProps) {
  return (
    <ul className="flex flex-col gap-2">
      {rows.map((invoice, index) => (
        <m.li
          key={invoice.id}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={staggerDelay(index)}
          className="glass grid grid-cols-1 items-center gap-3 rounded-2xl p-3 lg:grid-cols-[minmax(0,1fr)_auto_auto_auto_auto]"
        >
          <div className="min-w-0">
            <p className="truncate text-sm text-mist-100">{invoiceTitle(invoice)}</p>
            <p className="truncate text-xs text-mist-500">
              {invoice.studentName ?? "Học viên"} · {sourceLabelOf(invoice)}
            </p>
            <p className="truncate text-xs text-mist-500">
              Hạn {formatter.date(invoice.dueDate)}
              {invoice.dueExtended ? " · Đã gia hạn" : ""}
            </p>
          </div>
          <p className="text-sm text-mist-100">{formatter.count(invoice.amount)} đ</p>
          <p className="text-xs text-mist-500">Đã thu {formatter.count(invoice.amountPaid)} đ</p>
          <Badge tone={toneOf(invoice.status)}>{INVOICE_STATUS_LABEL[invoice.status]}</Badge>
          <div className="flex flex-wrap items-center gap-2">
            {renderActions?.(invoice)}
            <Link to={buildInvoiceDetailPath(invoice.id)}>
              <GlassButton variant="secondary" size="sm" icon={<ChevronRight size={14} aria-hidden />}>
                Xem chi tiết
              </GlassButton>
            </Link>
          </div>
        </m.li>
      ))}
    </ul>
  );
}
