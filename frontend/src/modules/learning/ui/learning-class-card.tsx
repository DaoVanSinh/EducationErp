import { formatScheduleSlot } from "@/entities/class";
import type { MyLearningClass } from "@/modules/learning/model/learning-schema";
import { enrollmentTone } from "@/modules/learning/model/learning-rules";
import { GradeList } from "@/modules/learning/ui/grade-list";
import { ENROLLMENT_STATUS_LABEL } from "@/shared/api/schemas";
import { Badge } from "@/shared/ui/badge";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { CalendarDays, UserRound } from "lucide-react";

export interface LearningClassCardProps {
  readonly item: MyLearningClass;
}

/** Một lớp trong cổng học tập: khóa học, giáo viên, lịch học, tiến trình và điểm. */
export function LearningClassCard({ item }: LearningClassCardProps) {
  return (
    <GlassPanel className="flex flex-col gap-4">
      <header className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <h2 className="truncate text-base font-semibold text-mist-100">{item.courseName ?? "(đã xoá)"}</h2>
          <p className="text-xs text-mist-400">Lớp {item.classCode ?? "(đã xoá)"}</p>
        </div>
        <Badge tone={enrollmentTone(item.status)}>{ENROLLMENT_STATUS_LABEL[item.status]}</Badge>
      </header>

      <div className="flex flex-col gap-2 text-sm text-mist-100">
        <p className="flex items-center gap-2">
          <UserRound size={14} aria-hidden /> Giáo viên: {item.teacherName ?? "Chưa phân công"}
        </p>
        <div className="flex flex-wrap items-center gap-2">
          <CalendarDays size={14} aria-hidden />
          <ScheduleChips item={item} />
        </div>
      </div>

      <ProgressBar item={item} />
      <GradeList grades={item.grades} finalScore={item.finalScore} />
    </GlassPanel>
  );
}

function ScheduleChips({ item }: LearningClassCardProps) {
  if (item.schedule.length === 0) {
    return <span className="text-xs text-mist-400">Chưa có lịch học</span>;
  }
  return (
    <>
      {item.schedule.map((slot) => (
        <Badge key={`${slot.dayOfWeek}-${slot.startTime}`} tone="neutral">
          {formatScheduleSlot(slot)}
        </Badge>
      ))}
    </>
  );
}

function ProgressBar({ item }: LearningClassCardProps) {
  if (item.progressPercent === null) {
    return <p className="text-xs text-mist-400">Khóa học chưa cấu hình tổng số buổi, chưa tính được tiến trình.</p>;
  }
  return (
    <div className="flex flex-col gap-1">
      <p className="text-xs text-mist-400">
        Tiến trình: đã học {item.elapsedSessions}/{item.standardSessionCount} buổi ({item.progressPercent}%)
      </p>
      <div
        className="h-2 w-full overflow-hidden rounded-full bg-slate-200/70"
        role="progressbar"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={item.progressPercent}
      >
        <div className="h-full rounded-full bg-orange-400" style={{ width: `${item.progressPercent}%` }} />
      </div>
    </div>
  );
}
