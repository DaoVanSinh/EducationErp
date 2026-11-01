import { useTiltCard } from "@/modules/landing/hooks/use-tilt-card";
import type { LandingCourse, LandingStats } from "@/modules/landing/model/landing-schema";
import { formatter } from "@/shared/lib/format";
import { m } from "framer-motion";
import { GraduationCap, Sparkles } from "lucide-react";

export interface LandingHeroVisualProps {
  readonly stats: LandingStats;
  readonly featuredCourse?: LandingCourse;
  readonly onExploreCourses: () => void;
}

export function LandingHeroVisual({
  stats,
  featuredCourse,
  onExploreCourses,
}: LandingHeroVisualProps) {
  const { cardRef, onMouseMove, onMouseEnter, onMouseLeave, rotateX, rotateY, scale } =
    useTiltCard(8);
  const courseTitle = featuredCourse?.name ?? "Lộ Trình Đào Tạo Chuẩn";
  const courseCode = featuredCourse?.code ?? "KHAI GIẢNG 2026";
  const courseTuition =
    featuredCourse?.tuitionFee !== null && featuredCourse?.tuitionFee !== undefined
      ? formatter.currency(featuredCourse.tuitionFee)
      : "Ưu đãi học phí";
  const maxDiscount = stats.maxComboDiscountPercent > 0 ? stats.maxComboDiscountPercent : 15;

  return (
    <div
      className="relative flex min-h-[500px] items-center justify-center"
      style={{ perspective: "1200px" }}
    >
      <m.div
        ref={cardRef}
        onMouseMove={onMouseMove}
        onMouseEnter={onMouseEnter}
        onMouseLeave={onMouseLeave}
        onClick={onExploreCourses}
        style={{
          rotateX,
          rotateY,
          scale,
          transformStyle: "preserve-3d",
        }}
        initial={{ opacity: 0, scale: 0.94 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={{ duration: 0.85 }}
        className="group relative flex w-full max-w-[540px] cursor-pointer items-center justify-center"
      >
        {/* Layer 1: Soft Ambient Pastel Contour Aura & Dashed Orbit */}
        <div
          aria-hidden
          className="pointer-events-none absolute z-0 size-[480px] rounded-full blur-2xl"
          style={{
            background:
              "radial-gradient(circle, rgba(255, 237, 213, 0.72) 0%, rgba(255, 241, 242, 0.52) 50%, transparent 75%)",
            transform: "translateZ(-30px)",
          }}
        />
        <div
          aria-hidden
          className="pointer-events-none absolute z-0 size-[430px] rounded-full border-[1.5px] border-dashed border-[#fdba74]/45"
          style={{ transform: "translateZ(-20px)" }}
        />

        {/* Layer 2: Cutout Student Model Image (No Background Box) */}
        <div
          style={{ transform: "translateZ(0px)" }}
          className="relative z-10 flex w-full items-center justify-center"
        >
          <img
            src="/images/viet_student_cutout.png"
            alt="Học viên tiêu biểu tại học viện"
            className="block h-auto max-h-[540px] w-full max-w-[450px] object-contain transition-transform duration-500 group-hover:scale-[1.03]"
            style={{
              filter:
                "drop-shadow(0 24px 36px rgba(255, 140, 66, 0.18)) drop-shadow(0 8px 16px rgba(15, 23, 42, 0.06))",
            }}
          />
        </div>

        {/* Layer 3: Floating 3D VIP Scholarship / Combo Card (Bottom-Right, strictly above model) */}
        <div
          style={{ transform: "translateZ(72px)" }}
          className="absolute -right-2 -bottom-2 z-30"
        >
          <m.div
            animate={{ y: [0, -6, 0] }}
            transition={{ duration: 4.2, repeat: Infinity, ease: "easeInOut" }}
            style={{
              background:
                "linear-gradient(135deg, rgba(255, 255, 255, 0.97) 0%, rgba(255, 247, 237, 0.95) 100%)",
            }}
            className="w-[268px] rounded-[22px] border-[1.5px] border-[#fed7aa] px-4.5 py-4 shadow-[0_20px_40px_-8px_rgba(255,140,66,0.24),0_6px_16px_rgba(15,23,42,0.06)] backdrop-blur-xl"
          >
            <div className="mb-2.5 flex items-center justify-between">
              <div className="flex items-center gap-1.5">
                <span className="flex size-6 items-center justify-center rounded-md bg-gradient-to-br from-[#ff8c42] to-[#ff5e62] text-white">
                  <Sparkles size={13} aria-hidden />
                </span>
                <span className="text-[11px] font-extrabold tracking-wide text-[#ea580c] uppercase">
                  HỌC BỔNG COMBO
                </span>
              </div>
              <span className="text-[10.5px] font-semibold text-[#64748b]">KỲ 2026</span>
            </div>

            <div className="mb-2 font-mono text-[15px] font-extrabold tracking-wider text-[#1e293b]">
              {courseCode} • ƯU ĐÃI
            </div>

            <div className="flex items-end justify-between">
              <div>
                <div className="text-[9px] font-bold tracking-wider text-[#94a3b8] uppercase">
                  ƯU ĐÃI HỌC PHÍ
                </div>
                <div className="text-xs font-extrabold text-[#ea580c]">
                  Giảm tới {maxDiscount}% Combo
                </div>
              </div>
              <div className="text-right">
                <div className="text-[9px] font-bold text-[#94a3b8] uppercase">HỌC PHÍ TỪ</div>
                <div className="text-[11px] font-bold text-[#1e293b]">{courseTuition}</div>
              </div>
            </div>
          </m.div>
        </div>

        {/* Layer 4: Floating Mini Course Badge (Middle-Left, strictly above model) */}
        <div
          style={{ transform: "translateZ(64px)" }}
          className="absolute top-[42%] -left-4 z-30"
        >
          <m.div
            animate={{ y: [0, 6, 0] }}
            transition={{ duration: 3.8, repeat: Infinity, ease: "easeInOut", delay: 0.4 }}
            className="flex max-w-[235px] items-center gap-2.5 rounded-[18px] border-[1.5px] border-[#fed7aa] bg-white/96 px-3.5 py-2.5 shadow-[0_14px_30px_-4px_rgba(255,140,66,0.22)] backdrop-blur-md"
          >
            <div className="flex size-9 shrink-0 items-center justify-center rounded-xl border border-[#fed7aa] bg-[#fff7ed] text-[#ea580c]">
              <GraduationCap size={19} aria-hidden />
            </div>
            <div className="min-w-0">
              <div className="truncate text-xs font-extrabold text-[#1e293b]">{courseTitle}</div>
              <div className="mt-0.5 text-[11px] font-bold text-[#ea580c]">
                {stats.activeClassCount} lớp mở • {stats.activeBranchCount} cơ sở
              </div>
            </div>
          </m.div>
        </div>

        {/* Layer 5: Floating Top-Right Mini Pill Badge (strictly above model) */}
        <div
          style={{ transform: "translateZ(60px)" }}
          className="absolute top-3 right-3 z-30"
        >
          <m.div
            animate={{ y: [0, -4, 0] }}
            transition={{ duration: 3.5, repeat: Infinity, ease: "easeInOut", delay: 0.2 }}
            className="flex items-center gap-2 rounded-full border border-[#fed7aa] bg-white/95 px-3.5 py-1.5 shadow-[0_10px_24px_-2px_rgba(255,140,66,0.2)] backdrop-blur-md"
          >
            <span className="flex size-4.5 items-center justify-center rounded-full bg-gradient-to-br from-[#ff8c42] to-[#ff5e62] text-[10px] font-extrabold text-white">
              ✓
            </span>
            <span className="text-[11.5px] font-extrabold text-[#1e293b]">
              Cam kết đầu ra <span className="font-bold text-[#ea580c]">• Tuyển sinh mới</span>
            </span>
          </m.div>
        </div>
      </m.div>
    </div>
  );
}
