import { useMyTaughtClasses } from "@/entities/attendance";
import { formatter } from "@/shared/lib/format";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { Skeleton } from "@/shared/ui/skeleton";

const MINUTES_PER_HOUR = 60;

/** Lớp đã dạy, kể cả lớp đã bàn giao cho giáo viên khác - để giáo viên đối chiếu với phiếu lương. */
export function TaughtClassesHistory() {
  const taught = useMyTaughtClasses();

  if (taught.isError) {
    return <ErrorNotice error={taught.error} />;
  }
  if (taught.isPending) {
    return <Skeleton className="h-24" />;
  }
  if (taught.data.length === 0) {
    return null;
  }
  return (
    <GlassPanel className="flex flex-col gap-3">
      <h2 className="text-sm font-semibold text-mist-100">Lịch sử giảng dạy</h2>
      <ul className="flex flex-col gap-2">
        {taught.data.map((item) => (
          <li
            key={item.classId}
            className="glass grid grid-cols-1 items-center gap-2 rounded-2xl p-3 sm:grid-cols-[minmax(0,1fr)_auto_auto]"
          >
            <div className="min-w-0">
              <p className="truncate text-sm text-mist-100">{item.classCode ?? "Lớp"}</p>
              <p className="truncate text-xs text-mist-500">{item.courseName ?? "Khoá học"}</p>
            </div>
            <p className="text-xs text-mist-500">
              {formatter.date(item.firstSessionDate)} - {formatter.date(item.lastSessionDate)}
            </p>
            <p className="text-xs tabular-nums text-mist-100">
              {item.sessionCount} buổi · {(item.totalMinutes / MINUTES_PER_HOUR).toFixed(1)} giờ
            </p>
          </li>
        ))}
      </ul>
    </GlassPanel>
  );
}
