import { INVOICE_STATUS, INVOICE_STATUS_LABEL, useInvoices } from "@/entities/billing";
import { buildInvoiceDetailPath } from "@/shared/constants/app-routes";
import { DEFAULT_PAGE_SIZE } from "@/shared/constants/query-config";
import { formatter } from "@/shared/lib/format";
import { Badge } from "@/shared/ui/badge";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { Pagination } from "@/shared/ui/pagination";
import { Skeleton } from "@/shared/ui/skeleton";
import { Receipt } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";

export interface StudentInvoicesListProps {
  readonly studentProfileId: string;
}

export function StudentInvoicesList({ studentProfileId }: StudentInvoicesListProps) {
  const [page, setPage] = useState(0);
  const invoices = useInvoices(page, DEFAULT_PAGE_SIZE, undefined, studentProfileId);

  if (invoices.isError) {
    return <ErrorNotice error={invoices.error} />;
  }
  if (invoices.isPending) {
    return <Skeleton className="h-16" />;
  }
  if (invoices.data.items.length === 0) {
    return (
      <EmptyState
        icon={<Receipt size={28} aria-hidden />}
        title="Chưa có hoá đơn nào"
        description="Học viên này chưa có hoá đơn học phí nào."
      />
    );
  }

  return (
    <div className="flex flex-col gap-3">
      <ul className="flex flex-col gap-2">
        {invoices.data.items.map((invoice) => (
          <li
            key={invoice.id}
            className="glass grid grid-cols-1 items-center gap-3 rounded-2xl p-3 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto]"
          >
            <Link to={buildInvoiceDetailPath(invoice.id)} className="truncate text-sm text-mist-100 hover:underline">
              Đợt {invoice.installmentNumber}
            </Link>
            <p className="truncate text-xs text-mist-400">
              {formatter.count(invoice.amountPaid)} / {formatter.count(invoice.amount)} đ
            </p>
            <Badge tone={invoice.status === INVOICE_STATUS.paid ? "positive" : "neutral"}>
              {INVOICE_STATUS_LABEL[invoice.status]}
            </Badge>
          </li>
        ))}
      </ul>
      <Pagination
        page={invoices.data.page}
        totalPages={invoices.data.totalPages}
        totalItems={invoices.data.totalItems}
        onPageChange={setPage}
        itemLabel="hoá đơn"
      />
    </div>
  );
}
