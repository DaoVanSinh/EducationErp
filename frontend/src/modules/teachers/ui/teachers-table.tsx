import type { TeacherProfileSummary } from "@/entities/teacher";
import { Can } from "@/entities/permission";
import { buildTeacherDetailPath } from "@/shared/constants/app-routes";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { formatter } from "@/shared/lib/format";
import { staggerDelay } from "@/shared/lib/motion";
import { Badge } from "@/shared/ui/badge";
import { GlassButton } from "@/shared/ui/glass-button";
import { m } from "framer-motion";
import { Pencil } from "lucide-react";
import { Link } from "react-router-dom";

export interface TeachersTableProps {
  readonly rows: readonly TeacherProfileSummary[];
  readonly onEdit: (teacher: TeacherProfileSummary) => void;
}

function workStatusLabel(teacher: TeacherProfileSummary): string {
  if (teacher.active) {
    return "Đang làm việc";
  }
  return teacher.lastWorkDate === null ? "Đã nghỉ" : `Đã nghỉ từ ${formatter.date(teacher.lastWorkDate)}`;
}

export function TeachersTable({ rows, onEdit }: TeachersTableProps) {
  return (
    <ul className="flex flex-col gap-2">
      {rows.map((teacher, index) => (
        <m.li
          key={teacher.id}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={staggerDelay(index)}
          className="glass grid grid-cols-1 items-center gap-3 rounded-2xl p-3 lg:grid-cols-[minmax(0,2fr)_minmax(0,2fr)_auto_auto]"
        >
          <Link to={buildTeacherDetailPath(teacher.id)} className="min-w-0 hover:underline">
            <p className="truncate text-sm text-mist-100">{teacher.fullName ?? "Chưa rõ tên"}</p>
            <p className="truncate text-xs text-mist-500">{teacher.email ?? "—"}</p>
          </Link>
          <p className="truncate text-xs text-mist-500">
            {teacher.homeBranchName ?? "Chưa thuộc chi nhánh nào"} ·{" "}
            {teacher.subjects.length === 0 ? "Chưa có môn dạy" : teacher.subjects.join(", ")}
          </p>
          <div className="flex flex-wrap gap-1">
            <Badge tone={teacher.active ? "positive" : "neutral"}>{workStatusLabel(teacher)}</Badge>
            {teacher.payConfigured ? null : <Badge tone="warning">Chưa thiết lập lương</Badge>}
          </div>
          <Can {...ACCESS_RULE.updateTeacher}>
            <div className="justify-self-start lg:justify-self-end">
              <GlassButton variant="secondary" size="sm" onClick={() => onEdit(teacher)} icon={<Pencil size={14} aria-hidden />}>
                Sửa
              </GlassButton>
            </div>
          </Can>
        </m.li>
      ))}
    </ul>
  );
}
