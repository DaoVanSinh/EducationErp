import type { TuitionCalculationResult } from "@/modules/landing/hooks/use-tuition-calculator";
import {
  LANDING_SECTION_ID,
  type LandingComboTier,
  type LandingCourse,
} from "@/modules/landing/model/landing-schema";
import { LandingSectionHeader } from "@/modules/landing/ui/landing-section-header";
import { formatter } from "@/shared/lib/format";
import { ArrowRight, Calculator, Check, Gift, Sparkles } from "lucide-react";

interface LandingCalculatorSectionProps {
  readonly courses: readonly LandingCourse[];
  readonly comboTiers: readonly LandingComboTier[];
  readonly selectedCourseIds: readonly string[];
  readonly calculation: TuitionCalculationResult;
  readonly onToggleCourse: (courseId: string) => void;
  readonly onSelectPresetCount: (count: number) => void;
  readonly onConsultCombo: () => void;
}

export function LandingCalculatorSection({
  courses,
  comboTiers,
  selectedCourseIds,
  calculation,
  onToggleCourse,
  onSelectPresetCount,
  onConsultCombo,
}: LandingCalculatorSectionProps) {
  return (
    <section id={LANDING_SECTION_ID.calculator} className="bg-white py-20 lg:py-24">
      <div className="mx-auto max-w-[1280px] px-4 sm:px-6 lg:px-8">
        <LandingSectionHeader
          badge="DỰ TOÁN HỌC PHÍ & ƯU ĐÃI COMBO"
          title="Công cụ tính"
          highlight="Học phí & Học bổng Combo"
          subtitle="Chọn các khoá học trong lộ trình của bạn để xem ngay mức ưu đãi học phí khi đăng ký theo gói Combo."
        />

        <div className="grid grid-cols-1 gap-8 lg:grid-cols-12">
          <div className="rounded-[28px] border border-[#ffedd5] bg-[#fffaf5] p-6 sm:p-8 lg:col-span-7">
            <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
              <div>
                <h3 className="text-[17px] font-extrabold text-[#1e293b]">1. Chọn nhanh gói lộ trình</h3>
                <p className="text-[13px] text-[#64748b]">
                  Bấm vào từng khoá học bên dưới để thêm hoặc bớt khỏi gói đăng ký
                </p>
              </div>
              <div className="flex flex-wrap gap-2">
                {[1, 2, 3].map((count) => (
                  <button
                    key={count}
                    type="button"
                    onClick={() => onSelectPresetCount(count)}
                    className={`cursor-pointer rounded-full px-3.5 py-1.5 text-[12px] font-bold transition-all ${
                      calculation.selectedCount === count
                        ? "bg-gradient-to-r from-[#ff8c42] to-[#ff5e62] text-white shadow-xs"
                        : "bg-white text-[#475569] ring-1 ring-[#fed7aa] hover:text-[#ea580c]"
                    }`}
                  >
                    Gói {count} khoá
                  </button>
                ))}
              </div>
            </div>

            {comboTiers.length > 0 && (
              <div className="mb-6 flex flex-wrap items-center gap-2 rounded-2xl border border-[#fed7aa] bg-[#fff7ed] p-3.5">
                <span className="inline-flex items-center gap-1.5 text-[12px] font-extrabold text-[#ea580c]">
                  <Gift className="h-4 w-4" />
                  Chính sách ưu đãi Combo:
                </span>
                {comboTiers.map((tier) => (
                  <span
                    key={tier.id}
                    className={`rounded-full px-3 py-1 text-[11.5px] font-bold ${
                      calculation.selectedCount >= tier.minCourseCount
                        ? "bg-gradient-to-r from-[#ff8c42] to-[#ff5e62] text-white"
                        : "bg-white text-[#ea580c] ring-1 ring-[#fed7aa]"
                    }`}
                  >
                    Từ {tier.minCourseCount} khoá: Giảm {tier.discountPercent}%
                  </span>
                ))}
              </div>
            )}

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              {courses.map((course) => {
                const isSelected = selectedCourseIds.includes(course.id);
                return (
                  <button
                    key={course.id}
                    type="button"
                    onClick={() => onToggleCourse(course.id)}
                    className={`flex cursor-pointer items-start justify-between gap-3 rounded-2xl border p-4 text-left transition-all ${
                      isSelected
                        ? "border-[#ff8c42] bg-white shadow-[0_8px_20px_rgba(255,140,66,0.16)]"
                        : "border-[#ffedd5] bg-white/85 hover:border-[#fdba74] hover:bg-white"
                    }`}
                  >
                    <div>
                      <span className="text-[11px] font-bold text-[#ea580c] uppercase">{course.code}</span>
                      <p className="mt-0.5 text-[14px] font-extrabold text-[#1e293b]">{course.name}</p>
                      <p className="mt-1 text-[12.5px] font-semibold text-[#475569]">
                        {course.tuitionFee !== null ? formatter.currency(course.tuitionFee) : "Liên hệ"} •{" "}
                        {course.standardSessionCount ?? 24} buổi
                      </p>
                    </div>
                    <span
                      className={`mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full border ${
                        isSelected
                          ? "border-[#ff8c42] bg-[#ff8c42] text-white"
                          : "border-[#fed7aa] bg-[#fff7ed] text-transparent"
                      }`}
                    >
                      <Check className="h-3.5 w-3.5" />
                    </span>
                  </button>
                );
              })}
            </div>
          </div>

          <div className="flex flex-col justify-between rounded-[28px] border border-[#fed7aa] bg-gradient-to-br from-[#fff7ed] via-white to-[#fff1f2] p-6 text-[#1e293b] shadow-[0_24px_56px_rgba(255,140,66,0.14)] sm:p-8 lg:col-span-5">
            <div>
              <div className="flex items-center justify-between">
                <span className="inline-flex items-center gap-1.5 rounded-full border border-[#fed7aa] bg-white px-3.5 py-1 text-[11px] font-bold tracking-wider text-[#ea580c] uppercase">
                  <Calculator className="h-3.5 w-3.5" />
                  Bảng dự toán trực tuyến
                </span>
                <span className="text-[12px] font-semibold text-[#64748b]">
                  {calculation.selectedCount} khoá • {calculation.totalSessions} buổi
                </span>
              </div>

              <div className="mt-6 rounded-2xl border border-[#fed7aa] bg-white/90 p-5 shadow-xs">
                <p className="text-[12px] font-semibold text-[#64748b] uppercase">
                  Học phí thực đóng sau ưu đãi Combo
                </p>
                <p className="mt-1 text-[32px] font-black tracking-tight text-[#ea580c] sm:text-[36px]">
                  {formatter.currency(calculation.netTuitionVnd)}
                </p>
                <p className="mt-1 text-[12.5px] font-semibold text-emerald-600">
                  Trung bình chỉ ~{formatter.currency(calculation.costPerSessionVnd)} / buổi học
                </p>
              </div>

              <div className="mt-5 space-y-3 text-[13.5px]">
                <div className="flex items-center justify-between text-[#475569]">
                  <span>Tổng học phí gốc ({calculation.selectedCount} khoá)</span>
                  <span className="font-bold text-[#1e293b]">{formatter.currency(calculation.baseTuitionVnd)}</span>
                </div>
                <div className="flex items-center justify-between text-[#475569]">
                  <span>Mức ưu đãi Combo lộ trình</span>
                  <span className="rounded-full border border-[#fed7aa] bg-[#fff7ed] px-2.5 py-0.5 text-[12px] font-bold text-[#ea580c]">
                    -{calculation.appliedDiscountPercent}%
                  </span>
                </div>
                <div className="flex items-center justify-between border-t border-[#fed7aa]/70 pt-3 text-emerald-600">
                  <span className="font-semibold">Tiết kiệm ngay</span>
                  <span className="text-[16px] font-black">-{formatter.currency(calculation.discountSavedVnd)}</span>
                </div>
              </div>

              {calculation.nextTierHint !== null && (
                <div className="mt-5 flex items-center gap-2 rounded-xl border border-[#fed7aa] bg-[#fff7ed] px-3.5 py-2.5 text-[12.5px] font-medium text-[#9a3412]">
                  <Sparkles className="h-4 w-4 shrink-0 text-[#ea580c]" />
                  <span>{calculation.nextTierHint}</span>
                </div>
              )}
            </div>

            <button
              type="button"
              onClick={onConsultCombo}
              className="mt-7 inline-flex w-full cursor-pointer items-center justify-center gap-2 rounded-full bg-gradient-to-r from-[#ff8c42] to-[#ff5e62] px-6 py-4 text-[14px] font-bold text-white shadow-[0_10px_28px_rgba(255,140,66,0.32)] transition-all hover:opacity-95"
            >
              Nhận báo giá & Giữ ưu đãi Combo này
              <ArrowRight className="h-4 w-4" />
            </button>
          </div>
        </div>
      </div>
    </section>
  );
}
