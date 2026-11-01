import type { TuitionCalculator } from "@/modules/landing/hooks/use-tuition-calculator";
import { GROUP_SIZE_LIMITS } from "@/modules/landing/model/tuition-calculator-rules";
import { CourseOption } from "@/modules/landing/ui/landing-calculator-course-picker";
import { Minus, Plus, Users } from "lucide-react";

/** Nhóm học chung: chọn MỘT khoá, rồi chọn số người cùng đăng ký. */
export function LandingCalculatorGroupPicker({ calculator }: { readonly calculator: TuitionCalculator }) {
  return (
    <>
      <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 className="text-[17px] font-extrabold text-[#1e293b]">Chọn khoá học chung</h3>
          <p className="text-[13px] text-[#64748b]">
            Cả nhóm học cùng một lớp. Mỗi người tự đóng học phí của mình và đều được giảm.
          </p>
        </div>
        <GroupSizeStepper value={calculator.groupSize} onChange={calculator.onChangeGroupSize} />
      </div>

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        {calculator.pricedCourses.map((course) => (
          <CourseOption
            key={course.id}
            course={course}
            selected={course.id === calculator.selectedGroupCourseId}
            onToggle={() => calculator.onSelectGroupCourse(course.id)}
          />
        ))}
      </div>
    </>
  );
}

function GroupSizeStepper({ value, onChange }: { readonly value: number; readonly onChange: (value: number) => void }) {
  return (
    <div className="flex items-center gap-2 rounded-full bg-white px-2 py-1.5 ring-1 ring-[#fed7aa]">
      <Users className="ml-1 h-4 w-4 text-[#ea580c]" aria-hidden />
      <button
        type="button"
        onClick={() => onChange(value - 1)}
        disabled={value <= GROUP_SIZE_LIMITS.min}
        aria-label="Bớt một người"
        className="flex h-7 w-7 cursor-pointer items-center justify-center rounded-full bg-[#fff7ed] text-[#ea580c] disabled:cursor-not-allowed disabled:opacity-40"
      >
        <Minus className="h-3.5 w-3.5" aria-hidden />
      </button>
      <span aria-live="polite" className="min-w-[64px] text-center text-[13px] font-extrabold text-[#1e293b]">
        {value} người
      </span>
      <button
        type="button"
        onClick={() => onChange(value + 1)}
        disabled={value >= GROUP_SIZE_LIMITS.max}
        aria-label="Thêm một người"
        className="flex h-7 w-7 cursor-pointer items-center justify-center rounded-full bg-[#fff7ed] text-[#ea580c] disabled:cursor-not-allowed disabled:opacity-40"
      >
        <Plus className="h-3.5 w-3.5" aria-hidden />
      </button>
    </div>
  );
}
