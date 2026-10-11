import { CONTRACT_STATUS, CONTRACT_STATUS_LABEL, CONTRACT_TYPE_LABEL, useMyContracts } from "@/entities/payroll";
import { DEFAULT_PAGE_SIZE } from "@/shared/constants/query-config";
import { Badge } from "@/shared/ui/badge";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { Pagination } from "@/shared/ui/pagination";
import { Skeleton } from "@/shared/ui/skeleton";
import { FileSignature } from "lucide-react";
import { useState } from "react";

/** Hợp đồng CỦA CHÍNH tài khoản đang đăng nhập - không cần quyền READ_PAYROLL
 * (PayrollSelfServiceController). Tài khoản không phải nhân viên (vd học viên) sẽ thấy danh sách
 * rỗng, không phải lỗi. */
export function MyContractsList() {
  const [page, setPage] = useState(0);
  const contracts = useMyContracts(page, DEFAULT_PAGE_SIZE);

  if (contracts.isError) {
    return <ErrorNotice error={contracts.error} />;
  }
  if (contracts.isPending) {
    return <Skeleton className="h-16" />;
  }
  if (contracts.data.items.length === 0) {
    return (
      <EmptyState
        icon={<FileSignature size={28} aria-hidden />}
        title="Chưa có hợp đồng nào"
        description="Tài khoản của bạn chưa có hợp đồng lao động nào được tạo."
      />
    );
  }

  return (
    <div className="flex flex-col gap-3">
      <ul className="flex flex-col gap-2">
        {contracts.data.items.map((contract) => (
          <li
            key={contract.id}
            className="glass grid grid-cols-1 items-center gap-3 rounded-2xl p-3 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_minmax(0,1fr)_auto]"
          >
            <div className="min-w-0">
              <p className="truncate text-sm text-mist-100">{CONTRACT_TYPE_LABEL[contract.contractType]}</p>
              <p className="truncate text-xs text-mist-500">
                {contract.baseSalary === null ? "—" : `${contract.baseSalary.toLocaleString("vi-VN")} đ/tháng`}
              </p>
            </div>
            <p className="truncate text-xs text-mist-400">
              {contract.startDate} → {contract.endDate ?? "chưa xác định"}
            </p>
            <p className="truncate text-xs text-mist-400">
              {contract.allowances.length === 0 ? "Không có phụ cấp" : `${contract.allowances.length} khoản phụ cấp`}
            </p>
            <Badge tone={contract.status === CONTRACT_STATUS.active ? "positive" : "neutral"}>
              {CONTRACT_STATUS_LABEL[contract.status]}
            </Badge>
          </li>
        ))}
      </ul>
      <Pagination
        page={contracts.data.page}
        totalPages={contracts.data.totalPages}
        totalItems={contracts.data.totalItems}
        onPageChange={setPage}
        itemLabel="hợp đồng"
      />
    </div>
  );
}
