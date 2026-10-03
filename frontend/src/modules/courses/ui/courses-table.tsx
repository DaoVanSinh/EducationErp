import type { CourseSummary } from "@/entities/course";
import { Can } from "@/entities/permission";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { staggerDelay } from "@/shared/lib/motion";
import { Badge } from "@/shared/ui/badge";
import { GlassButton } from "@/shared/ui/glass-button";
import { m } from "framer-motion";
import { Pencil } from "lucide-react";

export interface CoursesTableProps {
  readonly rows: readonly CourseSummary[];
  readonly onEdit: (course: CourseSummary) => void;
}

export function CoursesTable({ rows, onEdit }: CoursesTableProps) {
  return (
    <ul className="flex flex-col gap-2">
      {rows.map((course, index) => (
        <m.li
          key={course.id}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={staggerDelay(index)}
          className="glass grid grid-cols-1 items-center gap-3 rounded-2xl p-3 lg:grid-cols-[minmax(0,1fr)_minmax(0,2fr)_auto_auto]"
        >
          <p className="truncate text-sm text-mist-100">{course.code}</p>
          <div className="min-w-0">
            <p className="truncate text-sm text-mist-100">{course.name}</p>
            <p className="truncate text-xs text-mist-500">
              {course.standardSessionCount ? `${course.standardSessionCount} buổi chuẩn` : "Chưa có số buổi chuẩn"}
            </p>
          </div>
          <Badge tone={course.active ? "positive" : "neutral"}>
            {course.active ? "Đang hoạt động" : "Đã vô hiệu hoá"}
          </Badge>
          <Can {...ACCESS_RULE.updateCourse}>
            <div className="justify-self-start lg:justify-self-end">
              <GlassButton variant="secondary" size="sm" onClick={() => onEdit(course)} icon={<Pencil size={14} aria-hidden />}>
                Sửa
              </GlassButton>
            </div>
          </Can>
        </m.li>
      ))}
    </ul>
  );
}
