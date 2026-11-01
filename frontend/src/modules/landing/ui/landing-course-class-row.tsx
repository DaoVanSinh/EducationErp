import { formatScheduleSlot } from "@/entities/class";
import { landingClassAvailability } from "@/modules/landing/model/landing-class-rules";
import type { LandingClass } from "@/modules/landing/model/landing-schema";
import { Building2, CalendarClock, UserCheck, Users } from "lucide-react";

interface LandingCourseClassRowProps {
  readonly cls: LandingClass;
  readonly onReserve: () => void;
}

/** Một lớp đang mở trong chi tiết khoá học: trạng thái, ngày khai giảng, chỗ trống, giáo viên, lịch học. */
export function LandingCourseClassRow({ cls, onReserve }: LandingCourseClassRowProps) {
  const availability = landingClassAvailability(cls);
  return (
    <div className="flex flex-col justify-between gap-3 rounded-2xl border border-[#ffedd5] bg-[#fffaf5] p-4 sm:flex-row sm:items-center">
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-2">
          <span className="rounded-lg border border-[#fed7aa] bg-[#fff7ed] px-2.5 py-0.5 text-[11.5px] font-bold text-[#ea580c]">
            Lớp {cls.code}
          </span>
          {cls.branchName && (
            <span className="inline-flex items-center gap-1 text-[12.5px] font-semibold text-[#ea580c]">
              <Building2 className="h-3.5 w-3.5" aria-hidden />
              {cls.branchName}
            </span>
          )}
        </div>
        <p className="mt-2 inline-flex items-center gap-1.5 text-[12.5px] font-semibold text-[#1e293b]">
          <CalendarClock className="h-3.5 w-3.5 text-[#ea580c]" aria-hidden />
          {availability.timeline}
        </p>
        <div className="mt-1.5 flex flex-wrap gap-3 text-[12.5px] text-[#475569]">
          {cls.teacherName && (
            <span className="inline-flex items-center gap-1">
              <UserCheck className="h-3.5 w-3.5 text-emerald-600" aria-hidden />
              GV: {cls.teacherName}
            </span>
          )}
          <span className={`inline-flex items-center gap-1 ${availability.isFull ? "font-semibold text-[#f04452]" : ""}`}>
            <Users className="h-3.5 w-3.5 text-[#64748b]" aria-hidden />
            {availability.seats}
          </span>
        </div>
        {cls.schedule.length > 0 && (
          <div className="mt-2 flex flex-wrap gap-1.5">
            {cls.schedule.map((slot) => (
              <span
                key={`${slot.dayOfWeek}-${slot.startTime}`}
                className="rounded-lg border border-[#fed7aa] bg-white px-2.5 py-1 text-[11.5px] font-semibold text-[#ea580c]"
              >
                {formatScheduleSlot(slot)}
              </span>
            ))}
          </div>
        )}
      </div>
      <button
        type="button"
        onClick={onReserve}
        className="shrink-0 cursor-pointer rounded-full bg-white px-4 py-2 text-[12.5px] font-bold text-[#ea580c] ring-1 ring-[#fed7aa] transition-all hover:bg-gradient-to-r hover:from-[#ff8c42] hover:to-[#ff5e62] hover:text-white"
      >
        {availability.reserveLabel}
      </button>
    </div>
  );
}
