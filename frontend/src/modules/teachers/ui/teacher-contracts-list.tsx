import { CONTRACT_STATUS, CONTRACT_STATUS_LABEL, CONTRACT_TYPE_LABEL, useContracts } from "@/entities/payroll";
import { DEFAULT_PAGE_SIZE } from "@/shared/constants/query-config";
import { Badge } from "@/shared/ui/badge";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { Pagination } from "@/shared/ui/pagination";
import { Skeleton } from "@/shared/ui/skeleton";
import { FileSignature } from "lucide-react";
import { useState } from "react";

export interface TeacherContractsListProps {
  readonly accountId: string;
}

/** Danh sách hợp đồng CHỈ ĐỌC của một giáo viên - sửa/kết thúc/tải file vẫn làm ở trang Hợp đồng
 * lao động, không phải ở đây (giữ nguyên hành vi hiện có, không mở rộng phạm vi). */
export function TeacherContractsList({ accountId }: TeacherContractsListProps) {
  const [page, setPage] = useState(0);
  const contracts = useContracts(page, DEFAULT_PAGE_SIZE, accountId);

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
        description="Giáo viên này chưa có hợp đồng lao động nào được tạo."
      />
    );
  }

  return (
    <div className="flex flex-col gap-3">
      <ul className="flex flex-col gap-2">
        {contracts.data.items.map((contract) => (
          <li
            key={contract.id}
            className="glass grid grid-cols-1 items-center gap-3 rounded-2xl p-3 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto]"
          >
            <p className="truncate text-sm text-mist-100">{CONTRACT_TYPE_LABEL[contract.contractType]}</p>
            <p className="truncate text-xs text-mist-400">
              {contract.startDate} → {contract.endDate ?? "chưa xác định"}
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
