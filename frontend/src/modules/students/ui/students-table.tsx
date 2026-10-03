import type { StudentProfileSummary } from "@/entities/student";
import { Can } from "@/entities/permission";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { staggerDelay } from "@/shared/lib/motion";
import { Badge } from "@/shared/ui/badge";
import { GlassButton } from "@/shared/ui/glass-button";
import { m } from "framer-motion";
import { Pencil } from "lucide-react";

export interface StudentsTableProps {
  readonly rows: readonly StudentProfileSummary[];
  readonly onEdit: (student: StudentProfileSummary) => void;
}

export function StudentsTable({ rows, onEdit }: StudentsTableProps) {
  return (
    <ul className="flex flex-col gap-2">
      {rows.map((student, index) => (
        <m.li
          key={student.id}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={staggerDelay(index)}
          className="glass grid grid-cols-1 items-center gap-3 rounded-2xl p-3 lg:grid-cols-[minmax(0,2fr)_minmax(0,1.5fr)_auto_auto]"
        >
          <div className="min-w-0">
            <p className="truncate text-sm text-mist-100">{student.fullName ?? "Chưa rõ tên"}</p>
            <p className="truncate text-xs text-mist-500">{student.email ?? "—"}</p>
          </div>
          <p className="truncate text-xs text-mist-500">{student.sourceChannel ?? "Chưa rõ nguồn"}</p>
          <Badge tone={student.active ? "positive" : "neutral"}>
            {student.active ? "Đang hoạt động" : "Đã vô hiệu hoá"}
          </Badge>
          <Can {...ACCESS_RULE.updateStudent}>
            <div className="justify-self-start lg:justify-self-end">
              <GlassButton variant="secondary" size="sm" onClick={() => onEdit(student)} icon={<Pencil size={14} aria-hidden />}>
                Sửa
              </GlassButton>
            </div>
          </Can>
        </m.li>
      ))}
    </ul>
  );
}
