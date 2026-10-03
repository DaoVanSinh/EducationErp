import type { TeacherProfileSummary } from "@/entities/teacher";
import { Can } from "@/entities/permission";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { staggerDelay } from "@/shared/lib/motion";
import { Badge } from "@/shared/ui/badge";
import { GlassButton } from "@/shared/ui/glass-button";
import { m } from "framer-motion";
import { Pencil } from "lucide-react";

export interface TeachersTableProps {
  readonly rows: readonly TeacherProfileSummary[];
  readonly onEdit: (teacher: TeacherProfileSummary) => void;
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
          <div className="min-w-0">
            <p className="truncate text-sm text-mist-100">{teacher.fullName ?? "Chưa rõ tên"}</p>
            <p className="truncate text-xs text-mist-500">{teacher.email ?? "—"}</p>
          </div>
          <p className="truncate text-xs text-mist-500">
            {teacher.subjects.length === 0 ? "Chưa có môn dạy" : teacher.subjects.join(", ")}
          </p>
          <Badge tone={teacher.active ? "positive" : "neutral"}>
            {teacher.active ? "Đang hoạt động" : "Đã vô hiệu hoá"}
          </Badge>
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
