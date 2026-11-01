import {
  CALCULATOR_MODE,
  type TuitionCalculationResult,
} from "@/modules/landing/model/tuition-calculator-rules";
import { formatter } from "@/shared/lib/format";
import { ArrowRight, Calculator, Sparkles } from "lucide-react";

interface LandingCalculatorSummaryProps {
  readonly calculation: TuitionCalculationResult;
  readonly onConsult: () => void;
}

/** Bảng dự toán bên phải - cùng một khung cho cả hai cách đăng ký, chỉ khác nhãn. */
export function LandingCalculatorSummary({ calculation, onConsult }: LandingCalculatorSummaryProps) {
  const isGroup = calculation.mode === CALCULATOR_MODE.group;
  return (
    <div className="flex flex-col justify-between rounded-[28px] border border-[#fed7aa] bg-gradient-to-br from-[#fff7ed] via-white to-[#fff1f2] p-6 text-[#1e293b] shadow-[0_24px_56px_rgba(255,140,66,0.14)] sm:p-8 lg:col-span-5">
      <div>
        <div className="flex items-center justify-between gap-2">
          <span className="inline-flex items-center gap-1.5 rounded-full border border-[#fed7aa] bg-white px-3.5 py-1 text-[11px] font-bold tracking-wider text-[#ea580c] uppercase">
            <Calculator className="h-3.5 w-3.5" aria-hidden />
            Bảng dự toán trực tuyến
          </span>
          <span className="text-[12px] font-semibold text-[#64748b]">
            {isGroup ? `${calculation.unitCount} người · 1 khoá` : `${calculation.unitCount} khoá`}
            {calculation.totalSessions > 0 ? ` • ${calculation.totalSessions} buổi` : ""}
          </span>
        </div>

        <HeadlineCard calculation={calculation} isGroup={isGroup} />

        <dl className="mt-5 space-y-3 text-[13.5px]">
          <Row label={isGroup ? `Học phí gốc cả nhóm (${calculation.unitCount} người)` : `Tổng học phí gốc (${calculation.unitCount} khoá)`}>
            <span className="font-bold text-[#1e293b]">{formatter.currency(calculation.baseTuitionVnd)}</span>
          </Row>
          <Row label={isGroup ? "Ưu đãi nhóm học chung" : "Mức ưu đãi Combo lộ trình"}>
            <span className="rounded-full border border-[#fed7aa] bg-[#fff7ed] px-2.5 py-0.5 text-[12px] font-bold text-[#ea580c]">
              -{calculation.appliedDiscountPercent}%
            </span>
          </Row>
          <div className="flex items-center justify-between border-t border-[#fed7aa]/70 pt-3 text-emerald-600">
            <dt className="font-semibold">{isGroup ? "Cả nhóm tiết kiệm" : "Tiết kiệm ngay"}</dt>
            <dd className="text-[16px] font-black">-{formatter.currency(calculation.discountSavedVnd)}</dd>
          </div>
        </dl>

        {calculation.nextTierHint !== null && (
          <div className="mt-5 flex items-center gap-2 rounded-xl border border-[#fed7aa] bg-[#fff7ed] px-3.5 py-2.5 text-[12.5px] font-medium text-[#9a3412]">
            <Sparkles className="h-4 w-4 shrink-0 text-[#ea580c]" aria-hidden />
            <span>{calculation.nextTierHint}</span>
          </div>
        )}
      </div>

      <button
        type="button"
        onClick={onConsult}
        className="mt-7 inline-flex w-full cursor-pointer items-center justify-center gap-2 rounded-full bg-gradient-to-r from-[#ff8c42] to-[#ff5e62] px-6 py-4 text-[14px] font-bold text-white shadow-[0_10px_28px_rgba(255,140,66,0.32)] transition-all hover:opacity-95"
      >
        {isGroup ? "Đăng ký nhóm & giữ ưu đãi" : "Nhận báo giá & Giữ ưu đãi Combo này"}
        <ArrowRight className="h-4 w-4" aria-hidden />
      </button>
    </div>
  );
}

function HeadlineCard({ calculation, isGroup }: { readonly calculation: TuitionCalculationResult; readonly isGroup: boolean }) {
  return (
    <div className="mt-6 rounded-2xl border border-[#fed7aa] bg-white/90 p-5 shadow-xs">
      <p className="text-[12px] font-semibold text-[#64748b] uppercase">
        {isGroup ? "Mỗi người đóng sau ưu đãi nhóm" : "Học phí thực đóng sau ưu đãi Combo"}
      </p>
      <p className="mt-1 text-[32px] font-black tracking-tight text-[#ea580c] sm:text-[36px]">
        {formatter.currency(calculation.perPersonVnd)}
      </p>
      {calculation.costPerSessionVnd > 0 && (
        <p className="mt-1 text-[12.5px] font-semibold text-emerald-600">
          Trung bình chỉ ~{formatter.currency(calculation.costPerSessionVnd)} / buổi học
        </p>
      )}
    </div>
  );
}

function Row({ label, children }: { readonly label: string; readonly children: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3 text-[#475569]">
      <dt>{label}</dt>
      <dd>{children}</dd>
    </div>
  );
}
