import { INVOICE_STATUS, INVOICE_STATUS_LABEL, type InvoiceSummary } from "@/entities/billing";
import { buildInvoiceDetailPath } from "@/shared/constants/app-routes";
import { formatter } from "@/shared/lib/format";
import { staggerDelay } from "@/shared/lib/motion";
import { Badge } from "@/shared/ui/badge";
import { GlassButton } from "@/shared/ui/glass-button";
import { m } from "framer-motion";
import { ChevronRight } from "lucide-react";
import { Link } from "react-router-dom";

export interface InvoicesTableProps {
  readonly rows: readonly InvoiceSummary[];
}

function toneOf(status: InvoiceSummary["status"]): "positive" | "neutral" {
  return status === INVOICE_STATUS.paid ? "positive" : "neutral";
}

/**
 * Một hoá đơn neo vào combo hoặc vào ghi danh, không bao giờ cả hai (CHECK constraint ở backend).
 * Nhánh cuối không bao giờ chạy trong dữ liệu hợp lệ, nhưng vẫn phải trả về một chuỗi: hiển thị
 * "Không rõ nguồn" tốt hơn là để trang trắng vì một ô dữ liệu lạ.
 */
function sourceLabelOf(invoice: InvoiceSummary): string {
  if (invoice.comboId !== null) {
    return `Combo ${invoice.comboId.slice(0, 8)}`;
  }
  if (invoice.enrollmentId !== null) {
    return `Ghi danh ${invoice.enrollmentId.slice(0, 8)}`;
  }
  return "Không rõ nguồn";
}

export function InvoicesTable({ rows }: InvoicesTableProps) {
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
            <p className="truncate text-sm text-mist-100">Đợt {invoice.installmentNumber}</p>
            <p className="truncate text-xs text-mist-500">{sourceLabelOf(invoice)}</p>
          </div>
          <p className="text-sm text-mist-100">{formatter.count(invoice.amount)} đ</p>
          <p className="text-xs text-mist-500">Đã thu {formatter.count(invoice.amountPaid)} đ</p>
          <Badge tone={toneOf(invoice.status)}>{INVOICE_STATUS_LABEL[invoice.status]}</Badge>
          <Link to={buildInvoiceDetailPath(invoice.id)}>
            <GlassButton variant="secondary" size="sm" icon={<ChevronRight size={14} aria-hidden />}>
              Xem chi tiết
            </GlassButton>
          </Link>
        </m.li>
      ))}
    </ul>
  );
}
