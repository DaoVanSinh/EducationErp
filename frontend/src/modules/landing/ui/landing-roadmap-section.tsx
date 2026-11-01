import { LANDING_SECTION_ID, type RoadmapStep } from "@/modules/landing/model/landing-schema";
import { LandingSectionHeader } from "@/modules/landing/ui/landing-section-header";
import { m } from "framer-motion";
import { CheckCircle2, Clock, Target } from "lucide-react";

interface LandingRoadmapSectionProps {
  readonly steps: readonly RoadmapStep[];
}

const THEME_STYLES: Record<string, { badge: string; border: string; stepBg: string }> = {
  blue: {
    badge: "bg-[#fff7ed] text-[#ea580c]",
    border: "border-[#fed7aa]",
    stepBg: "bg-gradient-to-br from-[#ff8c42] to-[#ff5e62] text-white",
  },
  rose: {
    badge: "bg-[#fff0f3] text-[#f04452]",
    border: "border-[#fecdd3]",
    stepBg: "bg-[#f04452] text-white",
  },
  purple: {
    badge: "bg-[#fff7ed] text-[#ea580c]",
    border: "border-[#fed7aa]",
    stepBg: "bg-[#fb923c] text-white",
  },
  amber: {
    badge: "bg-[#fffbeb] text-[#d97706]",
    border: "border-[#fde68a]",
    stepBg: "bg-[#d97706] text-white",
  },
};

export function LandingRoadmapSection({ steps }: LandingRoadmapSectionProps) {
  return (
    <section id={LANDING_SECTION_ID.roadmap} className="bg-[#fffaf5] py-20 lg:py-24">
      <div className="mx-auto max-w-[1280px] px-4 sm:px-6 lg:px-8">
        <LandingSectionHeader
          badge="LỘ TRÌNH ĐÀO TẠO CHUẨN HOÁ"
          title="Lộ trình"
          highlight="Đào tạo & Bứt phá chuẩn đầu ra"
          subtitle="Mỗi giai đoạn được thiết kế bài bản theo năng lực đầu vào, kết hợp đánh giá định kỳ và cam kết chuẩn đầu ra."
        />

        <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-4">
          {steps.map((item, idx) => {
            const style = THEME_STYLES[item.colorTheme] ?? THEME_STYLES.blue;
            return (
              <m.article
                key={item.id ?? item.step}
                initial={{ opacity: 0, y: 18 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.35, delay: idx * 0.07 }}
                className={`card-3d-hover flex flex-col justify-between rounded-[24px] border bg-white p-6 shadow-[0_8px_28px_rgba(255,140,66,0.06)] ${style?.border ?? "border-[#fed7aa]"}`}
              >
                <div>
                  <div className="mb-4 flex items-center justify-between">
                    <span
                      className={`flex h-10 w-10 items-center justify-center rounded-2xl text-[15px] font-black shadow-xs ${style?.stepBg ?? "bg-gradient-to-br from-[#ff8c42] to-[#ff5e62] text-white"}`}
                    >
                      {item.step}
                    </span>
                    <span
                      className={`rounded-full px-3 py-1 text-[11px] font-bold ${style?.badge ?? "bg-[#fff7ed] text-[#ea580c]"}`}
                    >
                      {item.badgeText}
                    </span>
                  </div>

                  <p className="hero-script text-[13px] font-semibold text-[#64748b]">
                    {item.subTag}
                  </p>
                  <h3 className="mt-1 text-[18px] font-extrabold leading-snug text-[#1e293b]">
                    {item.title}
                  </h3>

                  <div className="mt-3 flex items-center gap-1.5 text-[12px] font-semibold text-[#475569]">
                    <Clock className="h-3.5 w-3.5 text-[#ea580c]" />
                    <span>{item.duration}</span>
                  </div>

                  <ul className="mt-4 space-y-2.5 border-t border-[#ffedd5] pt-4">
                    {item.features.map((feat) => (
                      <li
                        key={feat}
                        className="flex items-start gap-2 text-[13px] leading-relaxed text-[#475569]"
                      >
                        <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-500" />
                        <span>{feat}</span>
                      </li>
                    ))}
                  </ul>
                </div>

                <div className="mt-5 flex items-center gap-2 rounded-xl border border-[#ffedd5] bg-[#fffaf5] p-3 text-[12px] font-bold text-[#1e293b]">
                  <Target className="h-4 w-4 shrink-0 text-[#ea580c]" />
                  <span>{item.target}</span>
                </div>
              </m.article>
            );
          })}
        </div>
      </div>
    </section>
  );
}
