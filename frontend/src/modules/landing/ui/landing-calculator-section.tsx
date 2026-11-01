import type { TuitionCalculator } from "@/modules/landing/hooks/use-tuition-calculator";
import { LANDING_SECTION_ID, type LandingComboTier } from "@/modules/landing/model/landing-schema";
import { CALCULATOR_MODE, type CalculatorMode } from "@/modules/landing/model/tuition-calculator-rules";
import { LandingCalculatorCoursePicker } from "@/modules/landing/ui/landing-calculator-course-picker";
import { LandingCalculatorGroupPicker } from "@/modules/landing/ui/landing-calculator-group-picker";
import { LandingCalculatorSummary } from "@/modules/landing/ui/landing-calculator-summary";
import { LandingSectionHeader } from "@/modules/landing/ui/landing-section-header";
import { BookCopy, Gift, Users } from "lucide-react";

interface LandingCalculatorSectionProps {
  readonly calculator: TuitionCalculator;
  readonly comboTiers: readonly LandingComboTier[];
  readonly onConsult: () => void;
}

const MODE_TABS: readonly { readonly mode: CalculatorMode; readonly label: string; readonly icon: typeof Users }[] = [
  { mode: CALCULATOR_MODE.multiCourse, label: "Một người, nhiều khoá", icon: BookCopy },
  { mode: CALCULATOR_MODE.group, label: "Rủ bạn học chung (nhóm)", icon: Users },
];

export function LandingCalculatorSection({ calculator, comboTiers, onConsult }: LandingCalculatorSectionProps) {
  return (
    <section id={LANDING_SECTION_ID.calculator} className="bg-white py-20 lg:py-24">
      <div className="mx-auto max-w-[1280px] px-4 sm:px-6 lg:px-8">
        <LandingSectionHeader
          badge="DỰ TOÁN HỌC PHÍ & ƯU ĐÃI COMBO"
          title="Công cụ tính"
          highlight="Học phí & Ưu đãi"
          subtitle="Đăng ký nhiều khoá cho một người, hoặc rủ bạn bè học chung một khoá — cả hai cách đều được giảm theo số suất học."
        />

        <div className="grid grid-cols-1 gap-8 lg:grid-cols-12">
          <div className="rounded-[28px] border border-[#ffedd5] bg-[#fffaf5] p-6 sm:p-8 lg:col-span-7">
            <ModeTabs mode={calculator.mode} onSetMode={calculator.onSetMode} />
            <TierChips comboTiers={comboTiers} unitCount={calculator.calculation.unitCount} mode={calculator.mode} />
            <ModePanel calculator={calculator} />
          </div>
          <LandingCalculatorSummary calculation={calculator.calculation} onConsult={onConsult} />
        </div>
      </div>
    </section>
  );
}

function ModePanel({ calculator }: { readonly calculator: TuitionCalculator }) {
  if (calculator.mode === CALCULATOR_MODE.group) {
    return <LandingCalculatorGroupPicker calculator={calculator} />;
  }
  return <LandingCalculatorCoursePicker calculator={calculator} />;
}

function ModeTabs({ mode, onSetMode }: { readonly mode: CalculatorMode; readonly onSetMode: (mode: CalculatorMode) => void }) {
  return (
    <div role="tablist" aria-label="Cách đăng ký" className="mb-6 grid grid-cols-1 gap-2 rounded-2xl bg-white p-1.5 ring-1 ring-[#fed7aa] sm:grid-cols-2">
      {MODE_TABS.map((tab) => {
        const active = tab.mode === mode;
        const Icon = tab.icon;
        return (
          <button
            key={tab.mode}
            type="button"
            role="tab"
            aria-selected={active}
            onClick={() => onSetMode(tab.mode)}
            className={`inline-flex cursor-pointer items-center justify-center gap-2 rounded-xl px-4 py-2.5 text-[13px] font-bold transition-all ${
              active ? "bg-gradient-to-r from-[#ff8c42] to-[#ff5e62] text-white shadow-xs" : "text-[#475569] hover:text-[#ea580c]"
            }`}
          >
            <Icon className="h-4 w-4" aria-hidden />
            {tab.label}
          </button>
        );
      })}
    </div>
  );
}

function TierChips({
  comboTiers,
  unitCount,
  mode,
}: {
  readonly comboTiers: readonly LandingComboTier[];
  readonly unitCount: number;
  readonly mode: CalculatorMode;
}) {
  if (comboTiers.length === 0) {
    return null;
  }
  const unit = mode === CALCULATOR_MODE.group ? "người" : "khoá";
  return (
    <div className="mb-6 flex flex-wrap items-center gap-2 rounded-2xl border border-[#fed7aa] bg-[#fff7ed] p-3.5">
      <span className="inline-flex items-center gap-1.5 text-[12px] font-extrabold text-[#ea580c]">
        <Gift className="h-4 w-4" aria-hidden />
        Chính sách ưu đãi:
      </span>
      {comboTiers.map((tier) => (
        <span
          key={tier.id}
          className={`rounded-full px-3 py-1 text-[11.5px] font-bold ${
            unitCount >= tier.minCourseCount
              ? "bg-gradient-to-r from-[#ff8c42] to-[#ff5e62] text-white"
              : "bg-white text-[#ea580c] ring-1 ring-[#fed7aa]"
          }`}
        >
          Từ {tier.minCourseCount} {unit}: Giảm {tier.discountPercent}%
        </span>
      ))}
    </div>
  );
}
