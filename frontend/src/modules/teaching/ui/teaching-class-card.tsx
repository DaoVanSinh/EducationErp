import { ClassStatusBadge, formatScheduleSlot, isOverdueUnderfilled, type ClassSummary } from "@/entities/class";
import { formatter } from "@/shared/lib/format";
import {
  buildClassAssignmentsPath,
  buildClassAttendancePath,
  buildClassGradebookPath,
  buildClassMiniTasksPath,
  buildClassProgressPath,
} from "@/shared/constants/app-routes";
import { Badge } from "@/shared/ui/badge";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import {
  AlertTriangle,
  BookOpen,
  CalendarDays,
  ClipboardCheck,
  Flag,
  LineChart,
  ListChecks,
  MapPin,
  NotebookPen,
  Users,
} from "lucide-react";
import { Link } from "react-router-dom";

export interface TeachingClassCardProps {
  readonly cls: ClassSummary;
  /** "YYYY-MM-DD" - để cảnh báo lớp quá ngày khai giảng mà chưa đủ sĩ số. */
  readonly today: string;
}

interface ClassProps {
  readonly cls: ClassSummary;
}

/** Một lớp giáo viên đang dạy: khóa học, chi nhánh, lịch, sĩ số và lối vào sổ điểm/tiến trình. */
export function TeachingClassCard({ cls, today }: TeachingClassCardProps) {
  return (
    <GlassPanel className="flex flex-col gap-4">
      <header className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <h2 className="truncate text-base font-semibold text-mist-100">{cls.courseName}</h2>
          <p className="text-xs text-mist-400">Lớp {cls.code}</p>
        </div>
        <ClassStatusBadge status={cls.status} />
      </header>

      <OverdueNotice cls={cls} today={today} />

      <div className="flex flex-col gap-2 text-sm text-mist-100">
        <p className="flex items-center gap-2">
          <MapPin size={14} aria-hidden /> {cls.branchName ?? "—"}
        </p>
        <p className="flex items-center gap-2">
          <Users size={14} aria-hidden /> {cls.activeEnrollments}/{cls.maxSeats} học viên
        </p>
        <p className="flex items-center gap-2">
          <Flag size={14} aria-hidden /> Khai giảng {formatter.date(cls.startDate)}
        </p>
        <div className="flex flex-wrap items-center gap-2">
          <CalendarDays size={14} aria-hidden />
          <ScheduleChips cls={cls} />
        </div>
      </div>

      <div className="flex flex-wrap gap-2">
        <Link to={buildClassGradebookPath(cls.id)}>
          <GlassButton size="sm" icon={<BookOpen size={14} aria-hidden />}>
            Học viên &amp; sổ điểm
          </GlassButton>
        </Link>
        <Link to={buildClassProgressPath(cls.id)}>
          <GlassButton variant="secondary" size="sm" icon={<LineChart size={14} aria-hidden />}>
            Tiến trình học tập
          </GlassButton>
        </Link>
        <Link to={buildClassAttendancePath(cls.id)}>
          <GlassButton variant="secondary" size="sm" icon={<ClipboardCheck size={14} aria-hidden />}>
            Điểm danh
          </GlassButton>
        </Link>
        <Link to={buildClassAssignmentsPath(cls.id)}>
          <GlassButton variant="secondary" size="sm" icon={<NotebookPen size={14} aria-hidden />}>
            Bài tập & mini-test
          </GlassButton>
        </Link>
        <Link to={buildClassMiniTasksPath(cls.id)}>
          <GlassButton variant="secondary" size="sm" icon={<ListChecks size={14} aria-hidden />}>
            Mini-task
          </GlassButton>
        </Link>
      </div>
    </GlassPanel>
  );
}

/** Cùng luật với màn Lớp học của admin: quá ngày khai giảng mà chưa đủ sĩ số thì lớp chưa thể bắt đầu. */
function OverdueNotice({ cls, today }: TeachingClassCardProps) {
  if (!isOverdueUnderfilled(cls, today)) {
    return null;
  }
  return (
    <p className="flex items-center gap-1.5 rounded-xl bg-amber-50 px-3 py-2 text-xs font-medium text-amber-700">
      <AlertTriangle size={12} aria-hidden /> Quá ngày khai giảng ({formatter.date(cls.startDate)}) nhưng mới có{" "}
      {cls.activeEnrollments}/{cls.minSeats} học viên tối thiểu. Lớp chưa bắt đầu, chờ trung tâm dời ngày hoặc huỷ.
    </p>
  );
}

function ScheduleChips({ cls }: ClassProps) {
  if (cls.schedule.length === 0) {
    return <span className="text-xs text-mist-400">Chưa có lịch học</span>;
  }
  return (
    <>
      {cls.schedule.map((slot) => (
        <Badge key={`${slot.dayOfWeek}-${slot.startTime}`} tone="neutral">
          {formatScheduleSlot(slot)}
        </Badge>
      ))}
    </>
  );
}
