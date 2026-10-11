import { useEnrollments } from "@/entities/enrollment";
import { ENROLLMENT_STATUS, ENROLLMENT_STATUS_LABEL } from "@/shared/api/schemas";
import { DEFAULT_PAGE_SIZE } from "@/shared/constants/query-config";
import { Badge } from "@/shared/ui/badge";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { Pagination } from "@/shared/ui/pagination";
import { Skeleton } from "@/shared/ui/skeleton";
import { ClipboardList } from "lucide-react";
import { useState } from "react";

export interface StudentEnrollmentsListProps {
  readonly studentProfileId: string;
}

export function StudentEnrollmentsList({ studentProfileId }: StudentEnrollmentsListProps) {
  const [page, setPage] = useState(0);
  const enrollments = useEnrollments(page, DEFAULT_PAGE_SIZE, studentProfileId);

  if (enrollments.isError) {
    return <ErrorNotice error={enrollments.error} />;
  }
  if (enrollments.isPending) {
    return <Skeleton className="h-16" />;
  }
  if (enrollments.data.items.length === 0) {
    return (
      <EmptyState
        icon={<ClipboardList size={28} aria-hidden />}
        title="Chưa ghi danh lớp nào"
        description="Học viên này chưa được ghi danh vào lớp nào."
      />
    );
  }

  return (
    <div className="flex flex-col gap-3">
      <ul className="flex flex-col gap-2">
        {enrollments.data.items.map((enrollment) => (
          <li
            key={enrollment.id}
            className="glass grid grid-cols-1 items-center gap-3 rounded-2xl p-3 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)_auto]"
          >
            <p className="truncate text-xs text-mist-400">Ghi danh ngày {enrollment.enrolledAt}</p>
            <p className="truncate text-xs text-mist-400">
              {enrollment.withdrawnAt === null ? "—" : `Rút ngày ${enrollment.withdrawnAt}`}
            </p>
            <Badge tone={enrollment.status === ENROLLMENT_STATUS.active ? "positive" : "neutral"}>
              {ENROLLMENT_STATUS_LABEL[enrollment.status]}
            </Badge>
          </li>
        ))}
      </ul>
      <Pagination
        page={enrollments.data.page}
        totalPages={enrollments.data.totalPages}
        totalItems={enrollments.data.totalItems}
        onPageChange={setPage}
        itemLabel="ghi danh"
      />
    </div>
  );
}
