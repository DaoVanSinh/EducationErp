import { useCombos } from "@/entities/billing";
import { buildComboDetailPath } from "@/shared/constants/app-routes";
import { DEFAULT_PAGE_SIZE } from "@/shared/constants/query-config";
import { formatter } from "@/shared/lib/format";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { Pagination } from "@/shared/ui/pagination";
import { Skeleton } from "@/shared/ui/skeleton";
import { Package } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";

export interface StudentCombosListProps {
  readonly studentProfileId: string;
}

export function StudentCombosList({ studentProfileId }: StudentCombosListProps) {
  const [page, setPage] = useState(0);
  const combos = useCombos(page, DEFAULT_PAGE_SIZE, studentProfileId);

  if (combos.isError) {
    return <ErrorNotice error={combos.error} />;
  }
  if (combos.isPending) {
    return <Skeleton className="h-16" />;
  }
  if (combos.data.items.length === 0) {
    return (
      <EmptyState
        icon={<Package size={28} aria-hidden />}
        title="Chưa có combo nào"
        description="Học viên này chưa mua combo khoá học nào."
      />
    );
  }

  return (
    <div className="flex flex-col gap-3">
      <ul className="flex flex-col gap-2">
        {combos.data.items.map((combo) => (
          <li
            key={combo.id}
            className="glass grid grid-cols-1 items-center gap-3 rounded-2xl p-3 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto]"
          >
            <Link to={buildComboDetailPath(combo.id)} className="truncate text-sm text-mist-100 hover:underline">
              {combo.courseCount} khoá học
            </Link>
            <p className="truncate text-xs text-mist-400">
              {formatter.count(combo.totalDiscountedAmount)} đ (giảm {combo.discountPercent}%)
            </p>
            <p className="truncate text-xs text-mist-400">Hạn {combo.dueDate}</p>
          </li>
        ))}
      </ul>
      <Pagination
        page={combos.data.page}
        totalPages={combos.data.totalPages}
        totalItems={combos.data.totalItems}
        onPageChange={setPage}
        itemLabel="combo"
      />
    </div>
  );
}
