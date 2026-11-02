import { CalendarClock } from "lucide-react";

export interface ScheduleConflictNoticeProps {
  readonly message: string;
}

/** Cảnh báo chứ không phải lỗi: nhân viên vẫn được quyết định ghi danh sau khi đã biết các buổi trùng. */
export function ScheduleConflictNotice({ message }: ScheduleConflictNoticeProps) {
  return (
    <div
      role="alert"
      className="flex items-start gap-3 rounded-2xl border border-amber-200/90 bg-amber-50/90 px-4 py-3 shadow-xs"
    >
      <CalendarClock size={18} className="mt-0.5 shrink-0 text-amber-600" aria-hidden />
      <div className="flex-1 text-sm text-amber-950">
        <p className="font-medium">{message}</p>
        <p className="mt-1 text-xs text-amber-800">
          Hãy tư vấn học viên đổi sang lớp khác. Nếu học viên vẫn muốn học lớp này và đồng ý xử lý các buổi trùng, bấm
          "Vẫn ghi danh".
        </p>
      </div>
    </div>
  );
}
