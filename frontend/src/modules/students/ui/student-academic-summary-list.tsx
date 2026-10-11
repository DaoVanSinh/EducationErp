import { useStudentAcademicSummary } from "@/entities/academic";
import { ENROLLMENT_STATUS, ENROLLMENT_STATUS_LABEL } from "@/shared/api/schemas";
import { Badge } from "@/shared/ui/badge";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { Skeleton } from "@/shared/ui/skeleton";
import { GraduationCap } from "lucide-react";

export interface StudentAcademicSummaryListProps {
  readonly studentProfileId: string;
}

/** Danh sách rỗng ở đây có HAI nghĩa không phân biệt được từ phía giao diện: học viên chưa ghi
 * danh lớp nào, HOẶC người xem (giáo viên) không được phép thấy lớp nào của học viên này - cùng một
 * câu hiển thị cho cả hai, vì phân biệt chúng đòi lộ thông tin về phân công giảng dạy của người
 * khác mà người xem không có quyền biết. */
export function StudentAcademicSummaryList({ studentProfileId }: StudentAcademicSummaryListProps) {
  const summary = useStudentAcademicSummary(studentProfileId);

  if (summary.isError) {
    return <ErrorNotice error={summary.error} />;
  }
  if (summary.isPending) {
    return <Skeleton className="h-16" />;
  }
  if (summary.data.classes.length === 0) {
    return (
      <EmptyState
        icon={<GraduationCap size={28} aria-hidden />}
        title="Chưa có kết quả học tập"
        description="Không tìm thấy lớp nào có điểm/tiến trình hiển thị được cho tài khoản của bạn."
      />
    );
  }

  return (
    <ul className="flex flex-col gap-2">
      {summary.data.classes.map((row) => (
        <li
          key={row.classId}
          className="glass grid grid-cols-1 items-center gap-3 rounded-2xl p-3 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.6fr)_auto_auto_auto]"
        >
          <p className="truncate text-sm text-mist-100">{row.classCode ?? "(đã xoá)"}</p>
          <p className="truncate text-sm text-mist-100">{row.courseName ?? "(đã xoá)"}</p>
          <Badge tone={row.status === ENROLLMENT_STATUS.active ? "positive" : "neutral"}>
            {ENROLLMENT_STATUS_LABEL[row.status]}
          </Badge>
          <p className="text-xs text-mist-400">
            {row.finalScore === null ? "Chưa đủ điểm" : `Điểm: ${row.finalScore}`}
          </p>
          <p className="text-xs text-mist-400">
            {row.progressPercent === null ? "Chưa rõ tiến trình" : `${row.progressPercent}%`}
          </p>
        </li>
      ))}
    </ul>
  );
}
