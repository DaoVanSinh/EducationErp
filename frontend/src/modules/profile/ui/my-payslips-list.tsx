import { CONTRACT_TYPE_LABEL, useMyPayslips } from "@/entities/payroll";
import { DEFAULT_PAGE_SIZE } from "@/shared/constants/query-config";
import { Badge } from "@/shared/ui/badge";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { Pagination } from "@/shared/ui/pagination";
import { Skeleton } from "@/shared/ui/skeleton";
import { Wallet } from "lucide-react";
import { useState } from "react";

/** Phiếu lương CỦA CHÍNH tài khoản đang đăng nhập, xuyên mọi kỳ lương - không cần quyền
 * READ_PAYROLL (PayrollSelfServiceController). */
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
        description="Tài khoản của bạn chưa có phiếu lương ở kỳ lương nào."
      />
    );
  }

  return (
    <div className="flex flex-col gap-3">
      <ul className="flex flex-col gap-2">
        {payslips.data.items.map((payslip) => (
          <li
            key={payslip.id}
            className="glass grid grid-cols-1 items-center gap-3 rounded-2xl p-3 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_minmax(0,1fr)_auto]"
          >
            <p className="truncate text-sm text-mist-100">
              Kỳ lương {payslip.month}/{payslip.year}
            </p>
            <p className="truncate text-xs text-mist-500">{CONTRACT_TYPE_LABEL[payslip.contractType]}</p>
            <p className="truncate text-xs text-mist-400">{payslip.netPay.toLocaleString("vi-VN")} đ thực nhận</p>
            {payslip.inProbation ? <Badge tone="neutral">Thử việc</Badge> : <span />}
          </li>
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
