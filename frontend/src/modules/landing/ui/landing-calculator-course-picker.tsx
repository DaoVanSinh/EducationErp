import type { TuitionCalculator } from "@/modules/landing/hooks/use-tuition-calculator";
import type { LandingCourse } from "@/modules/landing/model/landing-schema";
import { formatter } from "@/shared/lib/format";
import { Check } from "lucide-react";

const PRESET_COUNTS = [1, 2, 3] as const;

/** Một người, nhiều khoá: chọn nhanh gói hoặc bấm từng khoá để thêm/bớt. */
export function LandingCalculatorCoursePicker({ calculator }: { readonly calculator: TuitionCalculator }) {
  return (
    <>
      <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 className="text-[17px] font-extrabold text-[#1e293b]">Chọn nhanh gói lộ trình</h3>
          <p className="text-[13px] text-[#64748b]">Bấm vào từng khoá học bên dưới để thêm hoặc bớt khỏi gói</p>
        </div>
        <div className="flex flex-wrap gap-2">
          {PRESET_COUNTS.map((count) => (
            <button
              key={count}
              type="button"
              onClick={() => calculator.onSelectPresetCount(count)}
              className={`cursor-pointer rounded-full px-3.5 py-1.5 text-[12px] font-bold transition-all ${
                calculator.calculation.unitCount === count
                  ? "bg-gradient-to-r from-[#ff8c42] to-[#ff5e62] text-white shadow-xs"
                  : "bg-white text-[#475569] ring-1 ring-[#fed7aa] hover:text-[#ea580c]"
              }`}
            >
              Gói {count} khoá
            </button>
          ))}
        </div>
      </div>

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        {calculator.pricedCourses.map((course) => (
          <CourseOption
            key={course.id}
            course={course}
            selected={calculator.selectedCourseIds.includes(course.id)}
            onToggle={() => calculator.onToggleCourse(course.id)}
          />
        ))}
      </div>
    </>
  );
}

export function CourseOption({
  course,
  selected,
  onToggle,
}: {
  readonly course: LandingCourse;
  readonly selected: boolean;
  readonly onToggle: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onToggle}
      aria-pressed={selected}
      className={`flex cursor-pointer items-start justify-between gap-3 rounded-2xl border p-4 text-left transition-all ${
        selected
          ? "border-[#ff8c42] bg-white shadow-[0_8px_20px_rgba(255,140,66,0.16)]"
          : "border-[#ffedd5] bg-white/85 hover:border-[#fdba74] hover:bg-white"
      }`}
    >
      <div>
        <span className="text-[11px] font-bold text-[#ea580c] uppercase">{course.code}</span>
        <p className="mt-0.5 text-[14px] font-extrabold text-[#1e293b]">{course.name}</p>
        <p className="mt-1 text-[12.5px] font-semibold text-[#475569]">
          {formatter.currency(course.tuitionFee ?? 0)}
          {course.standardSessionCount !== null ? ` • ${course.standardSessionCount} buổi` : ""}
        </p>
      </div>
      <span
        className={`mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full border ${
          selected ? "border-[#ff8c42] bg-[#ff8c42] text-white" : "border-[#fed7aa] bg-[#fff7ed] text-transparent"
        }`}
      >
        <Check className="h-3.5 w-3.5" aria-hidden />
      </span>
    </button>
  );
}
