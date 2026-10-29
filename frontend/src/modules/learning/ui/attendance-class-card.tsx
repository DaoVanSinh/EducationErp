import { ATTENDANCE_STATUS, ATTENDANCE_STATUS_LABEL, type AttendanceStatus, type MyAttendanceClass } from "@/entities/attendance";
import { ENROLLMENT_STATUS_LABEL } from "@/shared/api/schemas";
import { formatter } from "@/shared/lib/format";
import { Badge } from "@/shared/ui/badge";
import { GlassPanel } from "@/shared/ui/glass-panel";

export interface AttendanceClassCardProps {
  readonly item: MyAttendanceClass;
}

const STATUS_TONE: Record<AttendanceStatus, "positive" | "warning" | "danger" | "neutral"> = {
  [ATTENDANCE_STATUS.present]: "positive",
  [ATTENDANCE_STATUS.late]: "warning",
  [ATTENDANCE_STATUS.absent]: "danger",
  [ATTENDANCE_STATUS.excused]: "neutral",
};

/** Điểm danh của học viên ở một lớp: tỷ lệ chuyên cần và lịch sử từng buổi (mới nhất trước). */
export function AttendanceClassCard({ item }: AttendanceClassCardProps) {
  const { summary } = item;
  return (
    <GlassPanel className="flex flex-col gap-4">
      <header className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <h2 className="truncate text-base font-semibold text-mist-100">{item.courseName ?? "(đã xoá)"}</h2>
          <p className="text-xs text-mist-400">Lớp {item.classCode ?? "(đã xoá)"} · {ENROLLMENT_STATUS_LABEL[item.enrollmentStatus]}</p>
        </div>
        <Badge tone="accent">
          Chuyên cần: {summary.attendancePercent === null ? "chưa có buổi" : `${summary.attendancePercent}%`}
        </Badge>
      </header>
      <p className="text-sm text-mist-100">
        Có mặt {summary.presentCount} · Đi muộn {summary.lateCount} · Vắng {summary.absentCount} · Có phép {summary.excusedCount}
      </p>
      {item.sessions.length === 0 ? (
        <p className="text-xs text-mist-400">Lớp chưa điểm danh buổi nào.</p>
      ) : (
        <ul className="flex flex-wrap gap-2">
          {item.sessions.map((session) => (
            <li key={session.sessionDate} title={session.note ?? undefined}>
              <Badge tone={STATUS_TONE[session.status]}>
                {formatter.date(session.sessionDate)}: {ATTENDANCE_STATUS_LABEL[session.status]}
              </Badge>
            </li>
          ))}
        </ul>
      )}
    </GlassPanel>
  );
}
