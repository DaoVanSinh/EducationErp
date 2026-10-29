import { ClassStatusBadge, isOverdueUnderfilled, type ClassSummary } from "@/entities/class";
import { Can } from "@/entities/permission";
import type { ClassTransition } from "@/modules/courses/api/use-courses-mutations";
import { ClassLifecycleActions } from "@/modules/courses/ui/class-lifecycle-actions";
import {
  buildClassAssignmentsPath,
  buildClassAttendancePath,
  buildClassGradebookPath,
  buildClassMiniTasksPath,
  buildClassProgressPath,
} from "@/shared/constants/app-routes";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { formatter } from "@/shared/lib/format";
import { staggerDelay } from "@/shared/lib/motion";
import { GlassButton } from "@/shared/ui/glass-button";
import { m } from "framer-motion";
import { AlertTriangle, BookOpen, ClipboardCheck, LineChart, ListChecks, NotebookPen } from "lucide-react";
import { Link } from "react-router-dom";

export interface ClassesTableProps {
  readonly rows: readonly ClassSummary[];
  readonly today: string;
  readonly onEdit: (cls: ClassSummary) => void;
  readonly onTransition: (cls: ClassSummary, transition: ClassTransition) => void;
}

export function ClassesTable({ rows, today, onEdit, onTransition }: ClassesTableProps) {
  return (
    <ul className="flex flex-col gap-2">
      {rows.map((cls, index) => (
        <m.li
          key={cls.id}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={staggerDelay(index)}
          className="glass flex flex-col gap-3 rounded-2xl p-4"
        >
          <ClassHeader cls={cls} today={today} />
          <div className="flex flex-wrap items-center justify-between gap-2">
            <ClassLinks classId={cls.id} />
            <ClassLifecycleActions cls={cls} today={today} onEdit={onEdit} onTransition={onTransition} />
          </div>
        </m.li>
      ))}
    </ul>
  );
}

function ClassHeader({ cls, today }: { readonly cls: ClassSummary; readonly today: string }) {
  return (
    <div className="grid grid-cols-1 items-start gap-3 lg:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)_minmax(0,1fr)_auto]">
      <div className="min-w-0">
        <p className="truncate text-sm font-semibold text-slate-800">
          {cls.code} · {cls.courseName}
        </p>
        <p className="truncate text-xs text-slate-500">
          {cls.branchName ?? "—"} · {cls.teacherName ?? "Chưa gán giáo viên"}
        </p>
      </div>
      <div className="text-xs text-slate-600">
        <p>Khai giảng {formatter.date(cls.startDate)}</p>
        <p className="text-slate-400">{cls.endDate ? `Dự kiến kết thúc ${formatter.date(cls.endDate)}` : "Chưa có ngày kết thúc"}</p>
      </div>
      <SeatsCell cls={cls} today={today} />
      <ClassStatusBadge status={cls.status} />
    </div>
  );
}

function SeatsCell({ cls, today }: { readonly cls: ClassSummary; readonly today: string }) {
  const overdue = isOverdueUnderfilled(cls, today);
  return (
    <div className="text-xs text-slate-600">
      <p>
        <span className="font-semibold text-slate-800">{cls.activeEnrollments}</span>/{cls.maxSeats} học viên
      </p>
      <p className="text-slate-400">Tối thiểu {cls.minSeats} để khai giảng</p>
      {overdue ? (
        <p className="mt-1 flex items-center gap-1 font-medium text-amber-700">
          <AlertTriangle size={12} aria-hidden /> Quá ngày khai giảng, chưa đủ sĩ số
        </p>
      ) : null}
    </div>
  );
}

function ClassLinks({ classId }: { readonly classId: string }) {
  return (
    <div className="flex flex-wrap gap-1">
      <Can {...ACCESS_RULE.readGrade}>
        <Link to={buildClassGradebookPath(classId)}>
          <GlassButton variant="ghost" size="sm" icon={<BookOpen size={14} aria-hidden />}>
            Sổ điểm
          </GlassButton>
        </Link>
        <Link to={buildClassProgressPath(classId)}>
          <GlassButton variant="ghost" size="sm" icon={<LineChart size={14} aria-hidden />}>
            Tiến trình
          </GlassButton>
        </Link>
      </Can>
      <Can {...ACCESS_RULE.readAttendance}>
        <Link to={buildClassAttendancePath(classId)}>
          <GlassButton variant="ghost" size="sm" icon={<ClipboardCheck size={14} aria-hidden />}>
            Điểm danh
          </GlassButton>
        </Link>
      </Can>
      <Can {...ACCESS_RULE.readAssignment}>
        <Link to={buildClassAssignmentsPath(classId)}>
          <GlassButton variant="ghost" size="sm" icon={<NotebookPen size={14} aria-hidden />}>
            Bài tập
          </GlassButton>
        </Link>
        <Link to={buildClassMiniTasksPath(classId)}>
          <GlassButton variant="ghost" size="sm" icon={<ListChecks size={14} aria-hidden />}>
            Mini-task
          </GlassButton>
        </Link>
      </Can>
    </div>
  );
}
