import { ClassStatusBadge, useClasses } from "@/entities/class";
import { DEFAULT_PAGE_SIZE } from "@/shared/constants/query-config";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { Pagination } from "@/shared/ui/pagination";
import { Skeleton } from "@/shared/ui/skeleton";
import { CalendarDays } from "lucide-react";
import { useState } from "react";

export interface TeacherClassesListProps {
  readonly teacherAccountId: string;
}

/** Danh sách lớp CHỈ ĐỌC của một giáo viên - sửa lớp vẫn làm ở trang Lớp học, không phải ở đây
 * (giữ nguyên hành vi nút Sửa hiện có, không mở rộng phạm vi). */
export function TeacherClassesList({ teacherAccountId }: TeacherClassesListProps) {
  const [page, setPage] = useState(0);
  const classes = useClasses(page, DEFAULT_PAGE_SIZE, { teacherId: teacherAccountId });

  if (classes.isError) {
    return <ErrorNotice error={classes.error} />;
  }
  if (classes.isPending) {
    return <Skeleton className="h-16" />;
  }
  if (classes.data.items.length === 0) {
    return (
      <EmptyState
        icon={<CalendarDays size={28} aria-hidden />}
        title="Chưa dạy lớp nào"
        description="Giáo viên này chưa được phân công vào lớp nào."
      />
    );
  }

  return (
    <div className="flex flex-col gap-3">
      <ul className="flex flex-col gap-2">
        {classes.data.items.map((cls) => (
          <li
            key={cls.id}
            className="glass grid grid-cols-1 items-center gap-3 rounded-2xl p-3 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.6fr)_minmax(0,1fr)_auto]"
          >
            <p className="truncate text-sm text-mist-100">{cls.code}</p>
            <p className="truncate text-sm text-mist-100">{cls.courseName}</p>
            <p className="truncate text-xs text-mist-400">{cls.branchName ?? "—"}</p>
            <ClassStatusBadge status={cls.status} />
          </li>
        ))}
      </ul>
      <Pagination
        page={classes.data.page}
        totalPages={classes.data.totalPages}
        totalItems={classes.data.totalItems}
        onPageChange={setPage}
        itemLabel="lớp"
      />
    </div>
  );
}
