import type { ClassSummary } from "@/entities/class";
import { Can } from "@/entities/permission";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { staggerDelay } from "@/shared/lib/motion";
import { Badge } from "@/shared/ui/badge";
import { GlassButton } from "@/shared/ui/glass-button";
import { m } from "framer-motion";
import { Pencil } from "lucide-react";

export interface ClassesTableProps {
  readonly rows: readonly ClassSummary[];
  readonly onEdit: (cls: ClassSummary) => void;
}

export function ClassesTable({ rows, onEdit }: ClassesTableProps) {
  return (
    <ul className="flex flex-col gap-2">
      {rows.map((cls, index) => (
        <m.li
          key={cls.id}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={staggerDelay(index)}
          className="glass grid grid-cols-1 items-center gap-3 rounded-2xl p-3 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.6fr)_minmax(0,1fr)_minmax(0,1fr)_auto_auto]"
        >
          <p className="truncate text-sm text-mist-100">{cls.code}</p>
          <p className="truncate text-sm text-mist-100">{cls.courseName}</p>
          <p className="truncate text-xs text-mist-400">{cls.branchName ?? "—"}</p>
          <p className="truncate text-xs text-mist-400">{cls.teacherName ?? "Chưa gán giáo viên"}</p>
          <Badge tone={cls.active ? "positive" : "neutral"}>
            {cls.active ? "Đang hoạt động" : "Đã vô hiệu hoá"}
          </Badge>
          <Can {...ACCESS_RULE.updateClass}>
            <div className="justify-self-start lg:justify-self-end">
              <GlassButton variant="secondary" size="sm" onClick={() => onEdit(cls)} icon={<Pencil size={14} aria-hidden />}>
                Sửa
              </GlassButton>
            </div>
          </Can>
        </m.li>
      ))}
    </ul>
  );
}
