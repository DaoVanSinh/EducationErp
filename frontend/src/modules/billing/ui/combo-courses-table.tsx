import type { ComboEnrollment } from "@/entities/billing";
import { formatter } from "@/shared/lib/format";
import { Badge } from "@/shared/ui/badge";

export interface ComboCoursesTableProps {
  readonly rows: readonly ComboEnrollment[];
}

/** Học phí gốc ở đây là SNAPSHOT lúc tạo combo - đổi giá khoá học về sau không làm đổi con số này. */
export function ComboCoursesTable({ rows }: ComboCoursesTableProps) {
  return (
    <ul className="flex flex-col gap-2">
      {rows.map((member) => (
        <li
          key={member.id}
          className="glass grid grid-cols-1 items-center gap-3 rounded-2xl p-3 lg:grid-cols-[minmax(0,1fr)_auto_auto]"
        >
          <p className="truncate text-sm text-mist-100">Khoá {member.courseId.slice(0, 8)}</p>
          <Badge tone="neutral">Ghi danh {member.enrollmentId.slice(0, 8)}</Badge>
          <p className="text-sm text-mist-100">{formatter.count(member.originalTuitionFee)} đ</p>
        </li>
      ))}
    </ul>
  );
}
